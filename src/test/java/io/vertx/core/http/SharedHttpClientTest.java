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

package io.vertx.core.http;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.VertxInternal;
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

  int sharedPoolSize = 7;
  int clientVerticleInstances = 8;

  int requestsPerVerticle = 50 * sharedPoolSize;
  int requestsTotal = clientVerticleInstances * requestsPerVerticle;

  @Test
  public void testVerticlesUseSamePool() throws Exception {
    CountDownLatch receivedLatch = new CountDownLatch(requestsTotal);
    ServerVerticle serverVerticle = new ServerVerticle();

    vertx.deployVerticle(serverVerticle, onSuccess(serverId -> {

      HttpClientOptions clientOptions = httpClientOptions(serverVerticle, sharedPoolSize);
      DeploymentOptions deploymentOptions = deploymentOptions(clientVerticleInstances, clientOptions);

      Supplier<Verticle> verticleSupplier = () -> new ClientVerticle(clientVerticle -> {
        // Verify the reply context is the same as of the deployment
        // We can't compare to the verticle context because the reply context is a DuplicatedContext
        assertEquals(clientVerticle.context.deploymentID(), Vertx.currentContext().deploymentID());
        receivedLatch.countDown();
      });

      vertx.deployVerticle(verticleSupplier, deploymentOptions, onSuccess(clientId -> {
        vertx.eventBus().publish(ClientVerticle.TRIGGER_ADDRESS, requestsPerVerticle);
      }));
    }));

    waitUntil(() -> serverVerticle.connections.size() == sharedPoolSize);
    serverVerticle.replyLatch.complete();
    awaitLatch(receivedLatch);
    assertEquals(serverVerticle.maxConnections, sharedPoolSize);
  }

  @Test
  public void testSharedPoolClosedAutomatically() throws Exception {
    CountDownLatch receivedLatch = new CountDownLatch(requestsTotal);
    ServerVerticle serverVerticle = new ServerVerticle();
    AtomicReference<String> clientDeploymentId = new AtomicReference<>();

    vertx.deployVerticle(serverVerticle, onSuccess(serverId -> {

      HttpClientOptions clientOptions = httpClientOptions(serverVerticle, sharedPoolSize)
        // Make sure connections stay alive for the duration of the test if the server is not closed
        .setKeepAliveTimeout(3600);
      DeploymentOptions deploymentOptions = deploymentOptions(clientVerticleInstances, clientOptions);

      Supplier<Verticle> verticleSupplier = () -> new ClientVerticle(clientVerticle -> receivedLatch.countDown());

      vertx.deployVerticle(verticleSupplier, deploymentOptions, onSuccess(clientId -> {
        clientDeploymentId.set(clientId);
        vertx.eventBus().publish(ClientVerticle.TRIGGER_ADDRESS, requestsPerVerticle);
      }));
    }));

    waitUntil(() -> serverVerticle.connections.size() == sharedPoolSize);
    serverVerticle.replyLatch.complete();
    awaitLatch(receivedLatch);

    CountDownLatch undeployLatch = new CountDownLatch(1);
    vertx.undeploy(clientDeploymentId.get(), onSuccess(v -> {
      undeployLatch.countDown();
    }));

    awaitLatch(undeployLatch);
    assertWaitUntil(() -> serverVerticle.connections.size() == 0);
  }

  @Test
  public void testSharedPoolRetainedByOtherDeployment() throws Exception {
    int keepAliveTimeoutSeconds = 3;

    CountDownLatch receivedLatch = new CountDownLatch(requestsTotal);
    ServerVerticle serverVerticle = new ServerVerticle();
    AtomicReference<String> clientDeploymentId = new AtomicReference<>();

    vertx.deployVerticle(serverVerticle, onSuccess(serverId -> {

      HttpClientOptions clientOptions = httpClientOptions(serverVerticle, sharedPoolSize)
        .setKeepAliveTimeout(keepAliveTimeoutSeconds);
      DeploymentOptions deploymentOptions = deploymentOptions(clientVerticleInstances, clientOptions);

      Supplier<Verticle> verticleSupplier = () -> new ClientVerticle(clientVerticle -> receivedLatch.countDown());

      vertx.deployVerticle(verticleSupplier, deploymentOptions, onSuccess(clientId -> {
        clientDeploymentId.set(clientId);
        vertx.eventBus().publish(ClientVerticle.TRIGGER_ADDRESS, requestsPerVerticle);
      }));
    }));

    waitUntil(() -> serverVerticle.connections.size() == sharedPoolSize);

    CountDownLatch deployLatch = new CountDownLatch(1);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.createSharedHttpClient(ClientVerticle.SHARED_CLIENT_NAME, new HttpClientOptions());
      }
    }, onSuccess(v -> {
      deployLatch.countDown();
    }));
    awaitLatch(deployLatch);

    serverVerticle.replyLatch.complete();
    awaitLatch(receivedLatch);

    CountDownLatch undeployLatch = new CountDownLatch(1);
    vertx.undeploy(clientDeploymentId.get(), onSuccess(v -> {
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
      client = vertx.createSharedHttpClient(SHARED_CLIENT_NAME, new HttpClientOptions(config().getJsonObject("httpClientOptions")));
      vertx.eventBus().consumer(TRIGGER_ADDRESS, this).completionHandler(startPromise);
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
    volatile int port;
    Set<HttpConnection> connections = Collections.synchronizedSet(new HashSet<>());
    volatile int maxConnections;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      replyLatch = ((VertxInternal) vertx).promise();
      vertx.createHttpServer()
        .requestHandler(this)
        .listen(0)
        .onSuccess(server -> port = server.actualPort())
        .<Void>mapEmpty()
        .onComplete(startPromise);
    }

    @Override
    public void handle(HttpServerRequest req) {
      HttpConnection connection = req.connection();
      connections.add(connection);
      connection.closeHandler(v -> connections.remove(connection));
      maxConnections = Math.max(maxConnections, connections.size());
      replyLatch.future().onComplete(ar -> req.response().end());
    }
  }

  private static HttpClientOptions httpClientOptions(ServerVerticle serverVerticle, int sharedPoolSize) {
    return new HttpClientOptions()
      .setDefaultPort(serverVerticle.port)
      .setMaxPoolSize(sharedPoolSize);
  }

  private static DeploymentOptions deploymentOptions(int instances, HttpClientOptions options) {
    return new DeploymentOptions()
      .setInstances(instances)
      .setConfig(new JsonObject()
        .put("httpClientOptions", options.toJson()));
  }
}
