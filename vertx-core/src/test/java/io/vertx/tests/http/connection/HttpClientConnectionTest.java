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

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientConnection;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnectOptions;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.test.core.AssertExpectations.that;

public abstract class HttpClientConnectionTest extends HttpTestBase {

  protected HttpClientInternal client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.client = (HttpClientInternal) super.client;
  }

  @Test
  public void testGet() throws Exception {
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    startServer(testAddress);
    client.connect(new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort()))
      .compose(HttpClientConnection::request)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(body -> {
        assertEquals("Hello World", body.toString());
        testComplete();
      }));
    await();
  }

  @Test
  public void testStreamGet() throws Exception {
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    startServer(testAddress);
    HttpConnectOptions connect = new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort());
    client.connect(connect).compose(conn -> conn.request().compose(req -> req
        .send()
        .compose(HttpClientResponse::body)
        .expecting(that(body -> assertEquals("Hello World", body.toString())))
      ))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testConnectionClose() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer(testAddress);
    HttpConnectOptions connect = new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort());
    client.connect(connect).onComplete(onSuccess(conn -> {
      conn.closeHandler(v -> {
        complete();
      });
      conn.request()
        .compose(req -> req
          .send()
          .compose(HttpClientResponse::body))
        .onComplete(onFailure(err -> complete()));
    }));
    await();
  }

  @Test
  public void testRequestQueuing() throws Exception {
    int num = 1;
    server.requestHandler(req -> {
      req.response().putHeader("id", req.getHeader("id")).end();
    });
    startServer(testAddress);
    List<String> collected = Collections.synchronizedList(new ArrayList<>());
    client.connect(new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort())).onComplete(onSuccess(conn -> {
      final int concurrency = (int) conn.maxActiveStreams();
      for (int i = 0;i < concurrency + num;i++) {
        int val = i;
        conn.request().compose(req -> req
          .putHeader("id", "echo-" + val)
          .send()
          .map(response -> response.getHeader("id")))
          .onComplete(onSuccess(response -> {
            collected.add(response);
            if (val == concurrency + num - 1) {
              List<String> expected = IntStream.range(0, concurrency + num)
                .mapToObj(idx -> "echo-" + idx)
                .collect(Collectors.toList());
              assertEquals(expected, collected);
              testComplete();
            }
          }));
      }
    }));
    await();
  }
}
