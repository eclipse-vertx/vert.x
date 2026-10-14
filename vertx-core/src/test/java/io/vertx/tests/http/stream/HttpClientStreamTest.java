/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.stream;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.*;
import io.vertx.core.net.HostAndPort;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Test;

public abstract class HttpClientStreamTest extends SimpleHttpTest2 {

  private volatile Handler<HttpServerRequest> handler;
  private CleanableHttpClient client;

  public HttpClientStreamTest(HttpConfigurator config) {
    super(config);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    server.requestHandler(request -> {
      Handler<HttpServerRequest> requestHandler = handler;
      if (requestHandler != null) {
        requestHandler.handle(request);
      }
    });
    client = (CleanableHttpClient)super.client;
  }

  @Test
  public void testOpenStream(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    handler = request -> {
      request
        .response()
        .end("Hello World");
    };
    startServer(testAddress);
    HttpClientStream stream = client.openStream(requestOptions).await();
    stream.headHandler(head -> {
      checkpoint1.succeed();
    });
    stream.trailersHandler(trailers -> {
      checkpoint2.succeed();
    });
    HttpClientConnection connection = stream.connection();
    HttpRequestHead head = new HttpRequestHead(
      "https",
      HttpMethod.GET,
      "/",
      connection.newHttpRequestHeaders(),
      HostAndPort.authority("localhost", testAddress.port()),
      null);
    stream.writeHead(head, Buffer.buffer(), true);
  }
}
