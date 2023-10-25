/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package examples;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.docgen.Source;

import java.util.concurrent.CompletionStage;

@Source
public class VirtualThreadExamples {

  public void gettingStarted(Vertx vertx) {

    AbstractVerticle verticle = new AbstractVerticle() {
      @Override
      public void start() {
        HttpClient client = vertx.createHttpClient();
        HttpClientRequest req = client.request(
          HttpMethod.GET,
          8080,
          "localhost",
          "/").await();
        HttpClientResponse resp = req.send().await();
        int status = resp.statusCode();
        Buffer body = resp.body().await();
      }
    };

    // Run the verticle a on virtual thread
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD));
  }

  private int counter;

  public void fieldVisibility1() {
    int value = counter;
    value += getRemoteValue().await();
    // the counter value might have changed
    counter = value;
  }

  public void fieldVisibility2() {
    counter += getRemoteValue().await();
  }

  private Future<Buffer> callRemoteService() {
    return null;
  }

  private Future<Integer> getRemoteValue() {
    return null;
  }

  public void deployVerticle(Vertx vertx, int port) {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      HttpServer server;
      @Override
      public void start() {
        server = vertx
          .createHttpServer()
          .requestHandler(req -> {
            Buffer res;
            try {
              res = callRemoteService().await();
            } catch (Exception e) {
              req.response().setStatusCode(500).end();
              return;
            }
            req.response().end(res);
          });
        server.listen(port).await();
      }
    }, new DeploymentOptions()
      .setThreadingModel(ThreadingModel.VIRTUAL_THREAD));
  }

  public void awaitingFutures1(HttpClientResponse response) {
    Buffer body = response.body().await();
  }

  public void awaitingFutures2(HttpClientResponse response, CompletionStage<Buffer> completionStage) {
    Buffer body = Future.fromCompletionStage(completionStage).await();
  }

  private Future<String> getRemoteString() {
    return null;
  }

  public void awaitingMultipleFutures() {
    Future<String> f1 = getRemoteString();
    Future<Integer> f2 = getRemoteValue();
    CompositeFuture res = Future.all(f1, f2).await();
    String v1 = res.resultAt(0);
    Integer v2 = res.resultAt(1);
  }

  public void threadLocalSupport1(String userId, HttpClient client) {
    ThreadLocal<String> local = new ThreadLocal();
    local.set(userId);
    HttpClientRequest req = client.request(HttpMethod.GET, 8080, "localhost", "/").await();
    HttpClientResponse resp = req.send().await();
    // Thread local remains the same since it's the same virtual thread
  }
}
