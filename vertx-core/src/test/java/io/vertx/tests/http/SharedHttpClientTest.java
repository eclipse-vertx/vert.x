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

package io.vertx.tests.http;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.vertx.core.http.HttpMethod.GET;

public class SharedHttpClientTest extends VertxTestBase {

  private static final int SHARED_POOL_SIZE = 7;
  private static final int CLIENT_VERTICLE_INSTANCES = 8;
  private static final int NUM_REQUESTS_PER_VERTICLE = 50 * SHARED_POOL_SIZE;
  private static final int TOTAL_REQUESTS = CLIENT_VERTICLE_INSTANCES * NUM_REQUESTS_PER_VERTICLE;

  @Test
  public void testVerticlesUseSamePool() throws Exception {
    CountDownLatch receivedLatch = new CountDownLatch(TOTAL_REQUESTS);
    ServerVerticle serverVerticle = new ServerVerticle();

    vertx
      .deployVerticle(serverVerticle)
      .onComplete(onSuccess(serverId -> {

      DeploymentOptions deploymentOptions = deploymentOptions(
        CLIENT_VERTICLE_INSTANCES,
        new HttpClientOptions().setDefaultPort(HttpTest.DEFAULT_HTTP_PORT),
        new PoolOptions().setHttp1MaxSize(SHARED_POOL_SIZE));

      Supplier<Verticle> verticleSupplier = () -> new ClientVerticle(clientVerticle -> {
        // Verify the reply context is the same as of the deployment
        // We can't compare to the verticle context because the reply context is a DuplicatedContext
        assertEquals(clientVerticle.context.deploymentID(), Vertx.currentContext().deploymentID());
        receivedLatch.countDown();
      });

      vertx.deployVerticle(verticleSupplier, deploymentOptions).onComplete(onSuccess(clientId -> {
        vertx.eventBus().publish(ClientVerticle.TRIGGER_ADDRESS, NUM_REQUESTS_PER_VERTICLE);
      }));
    }));

    waitUntil(() -> serverVerticle.connections.size() == SHARED_POOL_SIZE);
    serverVerticle.replyLatch.complete();
    awaitLatch(receivedLatch);
    assertEquals(serverVerticle.maxConnections, SHARED_POOL_SIZE);
  }

  @Test
  public void testSharedPoolClosedAutomatically() throws Exception {
    CountDownLatch receivedLatch = new CountDownLatch(TOTAL_REQUESTS);
    ServerVerticle serverVerticle = new ServerVerticle();
    AtomicReference<String> clientDeploymentId = new AtomicReference<>();

    vertx.deployVerticle(serverVerticle).onComplete(onSuccess(serverId -> {

      HttpClientOptions clientOptions = new HttpClientOptions()
        .setDefaultPort(HttpTest.DEFAULT_HTTP_PORT)
        // Make sure connections stay alive for the duration of the test if the server is not closed
        .setKeepAliveTimeout(3600);
      DeploymentOptions deploymentOptions = deploymentOptions(CLIENT_VERTICLE_INSTANCES, clientOptions, new PoolOptions().setHttp1MaxSize(SHARED_POOL_SIZE));

      Supplier<Verticle> verticleSupplier = () -> new ClientVerticle(clientVerticle -> receivedLatch.countDown());

      vertx.deployVerticle(verticleSupplier, deploymentOptions).onComplete(onSuccess(clientId -> {
        clientDeploymentId.set(clientId);
        vertx.eventBus().publish(ClientVerticle.TRIGGER_ADDRESS, NUM_REQUESTS_PER_VERTICLE);
      }));
    }));

    waitUntil(() -> serverVerticle.connections.size() == SHARED_POOL_SIZE);
    serverVerticle.replyLatch.complete();
    awaitLatch(receivedLatch);

    CountDownLatch undeployLatch = new CountDownLatch(1);
    vertx.undeploy(clientDeploymentId.get()).onComplete(onSuccess(v -> {
      undeployLatch.countDown();
    }));

    awaitLatch(undeployLatch);
    assertWaitUntil(() -> serverVerticle.connections.size() == 0);
  }

  @Test
  public void testSharedPoolRetainedByOtherDeployment() throws Exception {
    int keepAliveTimeoutSeconds = 3;

    CountDownLatch receivedLatch = new CountDownLatch(TOTAL_REQUESTS);
    ServerVerticle serverVerticle = new ServerVerticle();
    AtomicReference<String> clientDeploymentId = new AtomicReference<>();

    vertx.deployVerticle(serverVerticle).onComplete(onSuccess(serverId -> {

      HttpClientOptions clientOptions = new HttpClientOptions()
        .setDefaultPort(HttpTest.DEFAULT_HTTP_PORT)
        .setKeepAliveTimeout(keepAliveTimeoutSeconds);
      DeploymentOptions deploymentOptions = deploymentOptions(CLIENT_VERTICLE_INSTANCES, clientOptions, new PoolOptions().setHttp1MaxSize(SHARED_POOL_SIZE));

      Supplier<Verticle> verticleSupplier = () -> new ClientVerticle(clientVerticle -> receivedLatch.countDown());

      vertx.deployVerticle(verticleSupplier, deploymentOptions).onComplete(onSuccess(clientId -> {
        clientDeploymentId.set(clientId);
        vertx.eventBus().publish(ClientVerticle.TRIGGER_ADDRESS, NUM_REQUESTS_PER_VERTICLE);
      }));
    }));

    waitUntil(() -> serverVerticle.connections.size() == SHARED_POOL_SIZE);

    CountDownLatch deployLatch = new CountDownLatch(1);
    vertx.deployVerticle(new AbstractVerticle() {

      // Keep a reference to the client otherwise it will be automatically closed when collected
      private HttpClient client;

      @Override
      public void start() {
        client = vertx.createHttpClient(new HttpClientOptions().setShared(true).setName(ClientVerticle.SHARED_CLIENT_NAME));
      }
    }).onComplete(onSuccess(v -> {
      deployLatch.countDown();
    }));
    awaitLatch(deployLatch);

    serverVerticle.replyLatch.complete();
    awaitLatch(receivedLatch);

    CountDownLatch undeployLatch = new CountDownLatch(1);
    vertx.undeploy(clientDeploymentId.get()).onComplete(onSuccess(v -> {
      undeployLatch.countDown();
    }));

    awaitLatch(undeployLatch);

    waitFor(2);
    vertx.setTimer((1000 * keepAliveTimeoutSeconds) / 2, l -> {
      assertTrue(serverVerticle.connections.size() > 0);
      complete();
    });
    vertx.setTimer(2 * 1000 * keepAliveTimeoutSeconds, l -> {
      assertTrue(serverVerticle.connections.size() == 0);
      complete();
    });
    await();
  }

  private static class ClientVerticle extends AbstractVerticle implements Handler<Message<Integer>> {

    static final String TRIGGER_ADDRESS = UUID.randomUUID().toString();
    static final String SHARED_CLIENT_NAME = UUID.randomUUID().toString();

    final Consumer<ClientVerticle> onResponseReceived;

    volatile Context context;
    HttpClient client;

    ClientVerticle(Consumer<ClientVerticle> onResponseReceived) {
      this.onResponseReceived = onResponseReceived;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      context = super.context;
      HttpClientOptions options = new HttpClientOptions(config().getJsonObject("httpClientOptions")).setShared(true).setName(SHARED_CLIENT_NAME);
      PoolOptions poolOptions = new PoolOptions(config().getJsonObject("poolOptions"));
      client = vertx.createHttpClient(options, poolOptions);
      vertx.eventBus().consumer(TRIGGER_ADDRESS, this).completion().onComplete(startPromise);
    }

    @Override
    public void handle(Message<Integer> message) {
      for (int i = 0; i < message.body(); i++) {
        client.request(GET, "/").compose(HttpClientRequest::send).onComplete(ar -> onResponseReceived.accept(this));
      }
    }
  }

  private static class ServerVerticle extends AbstractVerticle implements Handler<HttpServerRequest> {

    volatile Promise<Void> replyLatch;
    Set<HttpConnection> connections = Collections.synchronizedSet(new HashSet<>());
    volatile int maxConnections;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      replyLatch = ((VertxInternal) vertx).promise();
      vertx.createHttpServer()
        .connectionHandler(conn -> {
          connections.add(conn);
          conn.closeHandler(v -> connections.remove(conn));
          maxConnections = Math.max(maxConnections, connections.size());
        })
        .requestHandler(this)
        .listen(HttpTest.DEFAULT_HTTP_PORT)
        .<Void>mapEmpty()
        .onComplete(startPromise);
    }

    @Override
    public void handle(HttpServerRequest req) {
      replyLatch.future().onComplete(ar -> req.response().end());
    }
  }

  private static DeploymentOptions deploymentOptions(int instances, HttpClientOptions options, PoolOptions poolOptions) {
    return new DeploymentOptions()
      .setInstances(instances)
      .setConfig(new JsonObject()
        .put("httpClientOptions", options.toJson())
        .put("poolOptions", poolOptions.toJson())
      );
  }
}
