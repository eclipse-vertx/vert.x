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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationStream;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * @author Thomas Segismont
 */
public class RegistrationStreamTest extends VertxTestBase {

  private Helper helper = new Helper();

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
  }

  @Test
  public void testInitialState() throws Exception {
    startNodes(2);

    CompositeFuture setupFuture = CompositeFuture.all(Arrays.asList(
      helper.register(0, "foo"),
      helper.registerLocal(0, "foo"),
      helper.register(0, "bar"),

      helper.register(1, "bar"),
      helper.registerLocal(1, "bar"),
      helper.register(1, "foo")
    ));

    setupFuture
      .compose(v -> helper.getRegistrationStream(0, "foo"))
      .compose(stream -> helper.verify(() -> {
        List<RegistrationInfo> initialState = stream.initialState();
        Assert.assertEquals(3, initialState.size());
        List<RegistrationInfo> node_0_regs = helper.filterByNode(initialState, 0);
        Assert.assertEquals(2, node_0_regs.size());
        Assert.assertEquals(1, helper.filterLocalOnly(node_0_regs).size());
        List<RegistrationInfo> node_1_regs = helper.filterByNode(initialState, 1);
        Assert.assertEquals(1, node_1_regs.size());
        Assert.assertEquals(1, helper.filterClusterWide(node_1_regs).size());
      }))
      .compose(v -> helper.getRegistrationStream(1, "bar"))
      .compose(stream -> helper.verify(() -> {
        List<RegistrationInfo> initialState = stream.initialState();
        Assert.assertEquals(3, initialState.size());
        List<RegistrationInfo> node_0_regs = helper.filterByNode(initialState, 0);
        Assert.assertEquals(1, node_0_regs.size());
        Assert.assertEquals(1, helper.filterClusterWide(node_0_regs).size());
        List<RegistrationInfo> node_1_regs = helper.filterByNode(initialState, 1);
        Assert.assertEquals(2, node_1_regs.size());
        Assert.assertEquals(1, helper.filterLocalOnly(node_1_regs).size());
      }))
      .onSuccess(v -> testComplete())
      .onFailure(this::fail);

    await();
  }

  @Test
  public void testRegistrationUpdates() {
    startNodes(2);

    List<MessageConsumer<String>> consumers = Collections.synchronizedList(new ArrayList<>());
    AtomicReference<RegistrationStream> stream = new AtomicReference<>();
    List<RegistrationInfo> state = Collections.synchronizedList(new ArrayList<>());

    waitFor(2);

    helper.register(0, "foo")
      .onSuccess(consumers::add)
      .compose(v -> helper.getRegistrationStream(1, "foo"))
      .onSuccess(stream::set)
      .compose(v -> helper.verify(() -> Assert.assertEquals(1, stream.get().initialState().size())))
      .onSuccess(v -> {
        stream.get()
          .exceptionHandler(this::fail)
          .handler(list -> {
            synchronized (state) {
              state.clear();
              state.addAll(list);
            }
          })
          .endHandler(end -> complete())
          .start();
      })
      .compose(v -> helper.registerLocal(1, "foo"))
      .onSuccess(consumers::add)
      .compose(v -> helper.verify(() -> Assert.assertEquals(2, state.size()), 10, SECONDS))
      .compose(v -> helper.unregister(consumers.remove(0)))
      .compose(v -> helper.verify(() -> {
        Assert.assertEquals(1, state.size());
        Assert.assertEquals(1, helper.filterLocalOnly(state).size());
      }, 10, SECONDS))
      .compose(v -> helper.unregister(consumers.remove(0)))
      .onSuccess(v -> complete())
      .onFailure(this::fail);

    await();
  }

  @Test
  public void testNoEventsAfterStop() {
    startNodes(2);

    AtomicReference<RegistrationStream> stream = new AtomicReference<>();

    helper.register(0, "foo")
      .compose(v -> helper.getRegistrationStream(1, "foo"))
      .onSuccess(stream::set)
      .compose(v -> helper.verify(() -> Assert.assertEquals(1, stream.get().initialState().size())))
      .onSuccess(v -> {
        stream.get()
          .exceptionHandler(this::fail)
          .handler(list -> fail())
          .endHandler(end -> fail())
          .start();
      })
      .onSuccess(v -> stream.get().stop())
      .compose(v -> helper.registerLocal(1, "foo"))
      .compose(v -> helper.registerLocal(0, "foo"))
      .compose(v -> helper.register(1, "foo"))
      .onSuccess(v -> testComplete())
      .onFailure(this::fail);

    await();
  }

  @Test
  public void testRegistrationBeforeStart() {
    startNodes(2);

    AtomicReference<RegistrationStream> stream = new AtomicReference<>();
    List<RegistrationInfo> state = Collections.synchronizedList(new ArrayList<>());

    helper.register(0, "foo")
      .compose(v -> helper.getRegistrationStream(1, "foo"))
      .onSuccess(stream::set)
      .compose(v -> helper.verify(() -> Assert.assertEquals(1, stream.get().initialState().size())))
      .onSuccess(v -> {
        stream.get()
          .exceptionHandler(this::fail)
          .handler(list -> {
            synchronized (state) {
              state.clear();
              state.addAll(list);
            }
          });
      })
      .compose(v -> helper.registerLocal(1, "foo"))
      .compose(v -> helper.registerLocal(0, "foo"))
      .compose(v -> helper.register(1, "foo"))
      .onSuccess(v -> stream.get().start())
      .compose(v -> helper.verify(() -> Assert.assertEquals(4, state.size()), 10, SECONDS))
      .onSuccess(v -> complete())
      .onFailure(this::fail);

    await();
  }

  @Test
  public void testConcurrency() {
    startNodes(2);

    List<MessageConsumer<String>> consumers = Collections.synchronizedList(new ArrayList<>());
    AtomicReference<RegistrationStream> stream = new AtomicReference<>();
    AtomicInteger updates = new AtomicInteger();
    AtomicInteger last = new AtomicInteger();

    helper.register(0, "foo")
      .onSuccess(consumers::add)
      .compose(v -> helper.getRegistrationStream(1, "foo"))
      .onSuccess(stream::set)
      .onSuccess(v -> {
        stream.get()
          .exceptionHandler(this::fail)
          .handler(list -> helper.notConcurrent(() -> updates.incrementAndGet()))
          .endHandler(end -> helper.notConcurrent(() -> last.set(updates.get())));
      })
      .onSuccess(v -> stream.get().start())
      .compose(v -> {
        return IntStream.range(0, 1000)
          .mapToObj(i -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            return helper.register(random.nextInt(2), "foo", random.nextBoolean());
          })
          .peek(future -> future.onSuccess(consumers::add))
          .map(future -> Future.class.cast(future))
          .collect(collectingAndThen(toList(), CompositeFuture::all));
      })
      .compose(v -> helper.verify(() -> Assert.assertTrue(updates.get() > 0), 10, SECONDS))
      .compose(v -> {
        return consumers.stream()
          .map(helper::unregister)
          .map(future -> Future.class.cast(future))
          .collect(collectingAndThen(toList(), CompositeFuture::all));
      })
      .compose(v -> helper.verify(() -> {
        Assert.assertEquals(last.get(), updates.get());
      }, 10, SECONDS))
      .onSuccess(v -> complete())
      .onFailure(this::fail);

    await();
  }

  private class Helper {
    AtomicBoolean flag = new AtomicBoolean();

    VertxInternal getVertx(int nodeIndex) {
      return (VertxInternal) vertices[nodeIndex];
    }

    Future<RegistrationStream> getRegistrationStream(int nodeIndex, String address) {
      return getVertx(nodeIndex).getClusterManager().registrationListener(address);
    }

    Future<MessageConsumer<String>> register(int nodeIndex, String address) {
      return register(nodeIndex, address, false);
    }

    Future<MessageConsumer<String>> registerLocal(int nodeIndex, String address) {
      return register(nodeIndex, address, true);
    }

    Future<MessageConsumer<String>> register(int nodeIndex, String address, boolean local) {
      Objects.requireNonNull(address);

      EventBus eb = getVertx(nodeIndex).eventBus();

      MessageConsumer<String> consumer;
      if (local) {
        consumer = eb.localConsumer(address, msg -> msg.reply(msg.body()));
      } else {
        consumer = eb.consumer(address, msg -> msg.reply(msg.body()));
      }

      Promise<Void> promise = Promise.promise();
      consumer.completionHandler(promise);

      return promise.future().map(consumer);
    }

    Future<Void> unregister(MessageConsumer<String> consumer) {
      Promise<Void> promise = Promise.promise();
      consumer.unregister(promise);
      return promise.future();
    }

    List<RegistrationInfo> filterByNode(List<RegistrationInfo> list, int nodeIndex) {
      String nodeId = getVertx(nodeIndex).getClusterManager().getNodeId();
      return list.stream()
        .filter(registrationInfo -> registrationInfo.getNodeId().equals(nodeId))
        .collect(toList());
    }

    List<RegistrationInfo> filterLocalOnly(List<RegistrationInfo> list) {
      return list.stream()
        .filter(RegistrationInfo::isLocalOnly)
        .collect(toList());
    }

    List<RegistrationInfo> filterClusterWide(List<RegistrationInfo> list) {
      return list.stream()
        .filter(registrationInfo -> !registrationInfo.isLocalOnly())
        .collect(toList());
    }

    Future<Void> verify(Runnable task) {
      return verify(task, 0, SECONDS);
    }

    Future<Void> verify(Runnable task, long timeout, TimeUnit timeoutUnit) {
      Promise<Void> promise = Promise.promise();
      verify(task, 10, timeout, timeoutUnit, System.nanoTime(), promise);
      return promise.future();
    }

    void verify(Runnable task, long delay, long timeout, TimeUnit timeoutUnit, long start, Promise<Void> promise) {
      try {
        task.run();
        promise.complete();
      } catch (AssertionError e) {
        if (System.nanoTime() - start < NANOSECONDS.convert(timeout, timeoutUnit)) {
          vertx.setTimer(delay, l -> verify(task, delay, timeout, timeoutUnit, start, promise));
        } else {
          promise.fail(e);
        }
      }
    }

    void notConcurrent(Runnable runnable) {
      if (!flag.compareAndSet(false, true)) {
        fail();
      } else {
        runnable.run();
        if (!flag.compareAndSet(true, false)) {
          fail();
        }
      }
    }
  }
}
