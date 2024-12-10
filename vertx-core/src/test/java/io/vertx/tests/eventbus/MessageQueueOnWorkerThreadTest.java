/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.eventbus;

import io.vertx.core.*;
import io.vertx.core.eventbus.impl.clustered.Serializer;
import io.vertx.core.impl.VertxBootstrapImpl;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.impl.NodeSelector;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.Collections;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This test verifies the {@link Serializer} mechanism works when used from a worker context or execute blocking.
 * <p>
 * See <a href="https://github.com/eclipse-vertx/vert.x/issues/4128">issue on GitHub</a>
 */
public class MessageQueueOnWorkerThreadTest extends VertxTestBase {

  private Vertx vertx;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CustomNodeSelector selector = new CustomNodeSelector();
    VertxBootstrapImpl factory = new VertxBootstrapImpl().init().clusterManager(new FakeClusterManager()).clusterNodeSelector(selector);
    Future<Vertx> fut = factory.clusteredVertx();
    vertx = fut.await();
  }

  @Test
  public void testWorkerContext() throws Exception {
    test(true);
  }

  @Test
  public void testExecuteBlocking() throws Exception {
    test(false);
  }

  private void test(boolean worker) throws Exception {
    int senderInstances = 20, messagesToSend = 100, expected = senderInstances * messagesToSend;
    waitFor(expected);
    vertx.eventBus().consumer("foo", msg -> complete()).completion().onComplete(onSuccess(registered -> {
      DeploymentOptions options = new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setInstances(senderInstances);
      vertx.deployVerticle(() -> new SenderVerticle(worker, messagesToSend), options);
    }));
    await(5, SECONDS);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (vertx != null) {
        close(Collections.singletonList(vertx));
      }
    } finally {
      super.tearDown();
    }
  }

  private static class CustomNodeSelector implements NodeSelector {
    ClusterManager clusterManager;
    String nodeId;

    @Override
    public void init(Vertx vertx, ClusterManager clusterManager) {
      this.clusterManager = clusterManager;
    }

    @Override
    public void eventBusStarted() {
      nodeId = this.clusterManager.getNodeId();
    }

    @Override
    public void selectForSend(String address, Promise<String> promise) {
      try {
        NANOSECONDS.sleep(150);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      promise.tryComplete(nodeId);
    }

    @Override
    public void selectForPublish(String address, Promise<Iterable<String>> promise) {
      throw new UnsupportedOperationException();
    }
  }

  private class SenderVerticle extends AbstractVerticle {

    final boolean worker;
    int count;

    SenderVerticle(boolean worker, int count) {
      this.worker = worker;
      this.count = count;
    }

    @Override
    public void start() {
      sendMessage();
    }

    void sendMessage() {
      if (worker) {
        vertx.<Boolean>executeBlocking(() -> {
          if (count > 0) {
            vertx.eventBus().send("foo", "bar");
            count--;
            return true;
          } else {
            return false;
          }
        }).onComplete(onSuccess(cont -> {
          if (cont) {
            vertx.runOnContext(v -> sendMessage());
          }
        }));
      } else {
        if (count > 0) {
          vertx.eventBus().send("foo", "bar");
          count--;
          vertx.runOnContext(v -> sendMessage());
        }
      }
    }
  }
}
