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
package io.vertx.tests.http.connection;

import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnectOptions;
import org.junit.Test;

public class Http1xClientConnectionTest extends HttpClientConnectionTest {

  @Test
  public void testResetStreamBeforeSend() throws Exception {
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    startServer(testAddress);
    client.connect(new HttpConnectOptions().setServer(testAddress)).onComplete(onSuccess(conn -> {
      conn.request().onComplete(onSuccess(req -> {
        req.reset();
        vertx.runOnContext(v -> {
          conn.request()
            .compose(req2 -> req2
              .send()
              .compose(HttpClientResponse::body))
            .onComplete(onSuccess(body -> {
              assertEquals("Hello World", body.toString());
              testComplete();
            }));
        });
      }));
    }));
    await();
  }

  @Test
  public void testResetStreamRequestSent() throws Exception {
    waitFor(2);
    Promise<Void> continuation = Promise.promise();
    server.requestHandler(req -> {
      continuation.complete();
    });
    startServer(testAddress);
    HttpConnectOptions connect = new HttpConnectOptions().setServer(testAddress);
    client.connect(connect).onComplete(onSuccess(conn -> {
      conn.closeHandler(v -> complete());
      conn.request()
        .onComplete(onSuccess(req -> {
          req.send().onComplete(onFailure(err -> complete()));
          continuation
            .future()
            .onSuccess(v -> {
              req.reset();
            });
      }));
    }));
    await();
  }

  @Test
  public void testServerConnectionClose() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().putHeader("Connection", "close").end("Hello World");
    });
    startServer(testAddress);
    client.connect(new HttpConnectOptions().setServer(testAddress)).onComplete(onSuccess(conn -> {
      conn.closeHandler(v -> complete());
      conn
        .request()
        .compose(req -> req
          .send()
          .compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          assertEquals("Hello World", body.toString());
          complete();
        }));
    }));
    await();
  }
}
