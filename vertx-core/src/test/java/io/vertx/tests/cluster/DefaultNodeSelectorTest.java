/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.cluster;

import io.vertx.core.Completable;
import io.vertx.core.Promise;
import io.vertx.core.spi.cluster.ClusteredNode;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.core.eventbus.impl.clustered.DefaultNodeSelector;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class DefaultNodeSelectorTest {

  private static List<RegistrationInfo> registrations(String... nodeIds) {
    return Stream.of(nodeIds).map((nodeId -> new RegistrationInfo(nodeId, 0, false))).collect(Collectors.toList());
  }

  private static class ClusterView implements ClusteredNode {

    static class Op {
    }

    static class GetRegistrationsOp extends Op {
      private final String address;
      private final Completable<List<RegistrationInfo>> promise;
      GetRegistrationsOp(String address, Completable<List<RegistrationInfo>> promise) {
        this.address = address;
        this.promise = promise;
      }
      void succeed(String... nodeIds) {
        promise.succeed(registrations(nodeIds));
      }
      void fail(Throwable cause) {
        promise.fail(cause);
      }
    }

    private Deque<Op> log = new ArrayDeque<>();

    private GetRegistrationsOp assertGetRegistration() {
      Op op = log.poll();
      assertNotNull(op);
      assertTrue(op instanceof GetRegistrationsOp);
      return (GetRegistrationsOp) op;
    }

    private void assertEmpty() {
      assertEquals(null, log.poll());
    }

    @Override
    public String getNodeId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void getRegistrations(String address, Promise<List<RegistrationInfo>> promise) {
      log.add(new GetRegistrationsOp(address, promise));
    }

    @Override
    public NodeInfo getNodeInfo() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void getNodeInfo(String nodeId, Promise<NodeInfo> promise) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getNodes() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testSerializeSelect() {
    DefaultNodeSelector ns = new DefaultNodeSelector();
    ClusterView view = new ClusterView();
    ns.init(view);
    List<String> completions = new ArrayList<>();
    Completable<String> p1 = (result, failure) -> {
      if (result != null) {
        completions.add("p1");
      }
    };
    Completable<String> p2 = (result, failure) -> {
      if (result != null) {
        completions.add("p2");
      }
    };
    ns.selectForSend("the-address", p1);
    ns.selectForSend("the-address", p2);
    assertEquals(1, view.log.size());
    ClusterView.GetRegistrationsOp op = view.assertGetRegistration();
    assertEquals("the-address", op.address);
    assertEquals(List.of(), completions);
    op.succeed("node1");
    assertEquals(List.of("p1", "p2"), completions);
    view.assertEmpty();
  }

  @Test
  public void testSelectWhenBroadcasting() {
    DefaultNodeSelector ns = new DefaultNodeSelector();
    ClusterView view = new ClusterView();
    ns.init(view);
    List<String> completions = new ArrayList<>();
    Completable<String> p3 = (result, failure) -> {
      completions.add("p3");
    };
    Completable<String> p1 = (result, failure) -> {
      completions.add("p1");
      ns.selectForSend("the-address", p3);
    };
    Completable<String> p2 = (result, failure) -> {
      completions.add("p2");
    };
    ns.selectForSend("the-address", p1);
    ns.selectForSend("the-address", p2);
    assertEquals(1, view.log.size());
    ClusterView.GetRegistrationsOp op = view.assertGetRegistration();
    assertEquals("the-address", op.address);
    assertEquals(List.of(), completions);
    op.succeed("node1");
    assertEquals(List.of("p1", "p2", "p3"), completions);
    view.assertEmpty();
  }

  @Test
  public void testUpdateRegistration() {
    testRegistrationUpdate(true);
  }

  @Test
  public void testRemoveRegistration() {
    testRegistrationUpdate(false);
  }

  private void testRegistrationUpdate(boolean update) {
    DefaultNodeSelector ns = new DefaultNodeSelector();
    ClusterView view = new ClusterView();
    ns.init(view);
    AtomicInteger count = new AtomicInteger();
    for (int i = 0;i < 16;i++) {
      ns.selectForSend("the-address", (result, failure) -> {
        assertEquals("node1", result);
        count.incrementAndGet();
      });
    }
    ClusterView.GetRegistrationsOp get = view.assertGetRegistration();
    get.succeed("node1");
    assertEquals(16, count.get());
    ns.registrationsUpdated(new RegistrationUpdateEvent("the-address", update ? registrations("node2") : registrations()));
    for (int i = 0;i < 16;i++) {
      ns.selectForSend("the-address", (result, failure) -> {
        assertEquals("node2", result);
        count.incrementAndGet();
      });
    }
    if (update) {
      assertEquals(32, count.get());
    } else {
      get = view.assertGetRegistration();
      assertEquals("the-address", get.address);
    }
    view.assertEmpty();
  }

  @Test
  public void testGetRegistrationFailure() {
    DefaultNodeSelector ns = new DefaultNodeSelector();
    ClusterView view = new ClusterView();
    ns.init(view);
    AtomicInteger selects = new AtomicInteger();
    Exception cause = new Exception();
    for (int i = 0;i < 16;i++) {
      ns.selectForSend("the-address", (result, failure) -> {
        assertSame(cause, failure);
        selects.incrementAndGet();
      });
    }
    ClusterView.GetRegistrationsOp get = view.assertGetRegistration();
    view.assertEmpty();
    get.fail(cause);
    assertEquals(16, selects.get());
    ns.selectForSend("the-address", (result, failure) -> {
      selects.incrementAndGet();
    });
    view.assertGetRegistration();
    view.assertEmpty();
  }

  @Test
  public void testRegistrationUpdateBeforeGetResolution() {
    DefaultNodeSelector ns = new DefaultNodeSelector();
    ClusterView view = new ClusterView();
    ns.init(view);
    ns.selectForSend("the-address", (result, failure) -> {
    });
    ClusterView.GetRegistrationsOp get = view.assertGetRegistration();
    try {
      ns.registrationsUpdated(new RegistrationUpdateEvent("the-address", registrations("node1")));
      fail();
    } catch (UnsupportedOperationException expected) {
      // Is this a valid case ?
    }
  }

  @Test
  public void testEmptyRegistrations() {
    DefaultNodeSelector ns = new DefaultNodeSelector();
    ClusterView view = new ClusterView();
    ns.init(view);
    AtomicInteger count1 = new AtomicInteger();
    ns.selectForSend("the-address", (result, failure) -> {
      assertNull(result);
      count1.incrementAndGet();
    });
    AtomicInteger count2 = new AtomicInteger();
    ns.selectForPublish("the-address", (result, failure) -> {
      assertFalse(result.iterator().hasNext());
      count2.incrementAndGet();
    });
    ClusterView.GetRegistrationsOp get = view.assertGetRegistration();
    assertEquals("the-address", get.address);
    get.succeed();
    assertEquals(1, count1.get());
    assertEquals(1, count2.get());
    view.assertEmpty();
    ns.selectForSend("the-address", (result, failure) -> {
      fail();
    });
    get = view.assertGetRegistration();
    assertEquals("the-address", get.address);
    view.assertEmpty();
  }
}
