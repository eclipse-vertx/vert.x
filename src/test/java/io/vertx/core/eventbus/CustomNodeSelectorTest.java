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

package io.vertx.core.eventbus;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class CustomNodeSelectorTest extends VertxTestBase {

  private List<Vertx> vertices;

  @Test
  public void test() throws Exception {
    CompositeFuture startFuture = IntStream.range(0, 4)
      .mapToObj(i -> {
        VertxOptions vertxOptions = getOptions();
        vertxOptions.getEventBusOptions()
          .setClusterNodeMetadata(new JsonObject().put("rack", i % 2 == 0 ? "foo" : "bar"));
        return vertxOptions;
      })
      .map(options -> {
        VertxBuilder factory = new VertxBuilder(options).init().clusterNodeSelector(new CustomNodeSelector());
        Promise promise = Promise.promise();
        factory.clusteredVertx(promise);
        return promise.future();
      })
      .collect(collectingAndThen(toList(), CompositeFuture::all));

    CountDownLatch startLatch = new CountDownLatch(1);
    startFuture.onComplete(onSuccess(cf -> startLatch.countDown()));
    awaitLatch(startLatch);
    vertices = startFuture.list();

    ConcurrentMap<Integer, Set<String>> received = new ConcurrentHashMap<>();
    CountDownLatch latch = new CountDownLatch(8);
    CompositeFuture cf = IntStream.range(0, 4)
      .mapToObj(i -> vertices.get(i).eventBus().<String>consumer("test", msg -> {
        received.merge(i, Collections.singleton(msg.body()), (s1, s2) -> Stream.concat(s1.stream(), s2.stream()).collect(toSet()));
        latch.countDown();
      }))
      .map(consumer -> {
        Promise promise = Promise.promise();
        consumer.completionHandler(promise);
        return promise.future();
      })
      .collect(collectingAndThen(toList(), CompositeFuture::all));

    Map<Integer, Set<String>> expected = new HashMap<>();
    cf.onComplete(onSuccess(v -> {
      for (int i = 0; i < 4; i++) {
        String s = String.valueOf((char) ('a' + i));
        vertices.get(i).eventBus().publish("test", s);
        expected.merge(i, Collections.singleton(s), (s1, s2) -> Stream.concat(s1.stream(), s2.stream()).collect(toSet()));
        expected.merge((i + 2) % 4, Collections.singleton(s), (s1, s2) -> Stream.concat(s1.stream(), s2.stream()).collect(toSet()));
      }
    }));

    awaitLatch(latch);

    assertEquals(expected, received);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (vertices != null) {
        closeClustered(vertices);
      }
    } finally {
      super.tearDown();
    }
  }

  private static class CustomNodeSelector implements NodeSelector {
    private ClusterManager clusterManager;
    private String rack;

    @Override
    public void init(Vertx vertx, ClusterManager clusterManager) {
      this.clusterManager = clusterManager;
    }

    @Override
    public void eventBusStarted() {
      rack = this.clusterManager.getNodeInfo().metadata().getString("rack");
    }

    @Override
    public void selectForSend(Message<?> message, Promise<String> promise) {
      promise.fail("Not implemented");
    }

    @Override
    public void selectForPublish(Message<?> message, Promise<Iterable<String>> promise) {
      List<String> nodes = clusterManager.getNodes();
      CompositeFuture future = nodes.stream()
        .map(nodeId -> {
          Promise nodeInfo = Promise.promise();
          clusterManager.getNodeInfo(nodeId, nodeInfo);
          return nodeInfo.future();
        })
        .collect(collectingAndThen(toList(), CompositeFuture::all));
      future.<Iterable<String>>map(cf -> {
        List<String> res = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
          NodeInfo nodeInfo = cf.resultAt(i);
          if (nodeInfo.metadata().getString("rack").equals(this.rack)) {
            res.add(nodes.get(i));
          }
        }
        return res;
      }).onComplete(promise);
    }

    @Override
    public void registrationsUpdated(RegistrationUpdateEvent event) {
    }

    @Override
    public void registrationsLost() {
    }
  }
}
