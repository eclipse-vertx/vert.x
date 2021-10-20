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

package io.vertx.core.eventbus;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.impl.clustered.Serializer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.test.core.VertxTestBase;
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
    VertxBuilder factory = new VertxBuilder().init().clusterNodeSelector(selector);
    Promise<Vertx> promise = Promise.promise();
    factory.clusteredVertx(promise);
    vertx = promise.future().toCompletionStage().toCompletableFuture().get();
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
    vertx.eventBus().consumer("foo", msg -> complete()).completionHandler(onSuccess(registered -> {
      DeploymentOptions options = new DeploymentOptions().setWorker(worker).setInstances(senderInstances);
      vertx.deployVerticle(() -> new SenderVerticle(worker, messagesToSend), options);
    }));
    await(5, SECONDS);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (vertx != null) {
        closeClustered(Collections.singletonList(vertx));
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
    public void selectForSend(Message<?> message, Promise<String> promise) {
      try {
        NANOSECONDS.sleep(150);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      promise.tryComplete(nodeId);
    }

    @Override
    public void selectForPublish(Message<?> message, Promise<Iterable<String>> promise) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registrationsUpdated(RegistrationUpdateEvent event) {
    }

    @Override
    public void registrationsLost() {
    }
  }

  private static class SenderVerticle extends AbstractVerticle {

    final boolean worker;
    int count;

    SenderVerticle(boolean worker, int count) {
      this.worker = worker;
      this.count = count;
    }

    @Override
    public void start() throws Exception {
      sendMessage();
    }

    void sendMessage() {
      if (worker) {
        vertx.executeBlocking(prom -> {
          if (count > 0) {
            vertx.eventBus().send("foo", "bar");
            count--;
            prom.complete();
          }
        }, ar -> vertx.runOnContext(v -> sendMessage()));
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
