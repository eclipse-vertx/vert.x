/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.http.*;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HttpProtocolTest extends HttpTestBase {

  @Test
  public void testDefaultProtocol() throws Exception {
    server.requestHandler(request -> {
      request.response().send("" + request.version());
    });
    startServer(testAddress);

    String protocol = client.request(requestOptions)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)).await().toString();

    assertEquals("HTTP_1_1", protocol);
  }

  @Test
  public void testProtocolSelection() throws Exception {
    testProtocols("HTTP_2");
  }

  @Test
  public void testUnsupportedProtocol() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setHttp2ClearTextEnabled(false));
    testProtocols("HTTP_1_1");
  }

  private void testProtocols(String expectedProtocol) throws Exception {
    List<HttpConnection> connections = Collections.synchronizedList(new ArrayList<>());
    server.connectionHandler(connections::add);
    server.requestHandler(request -> {
      request.response().send("" + request.version());
    });
    startServer(testAddress);

    for (int i = 0;i < 8;i++) {
      String protocol = client.request(new RequestOptions(requestOptions).setProtocolVersion(HttpVersion.HTTP_2))
        .compose(request -> request
          .send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body)).await().toString();
      assertEquals(expectedProtocol, protocol);
    }

    for (int i = 0;i < 8;i++) {
      String protocol = client.request(requestOptions)
        .compose(request -> request
          .send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body)).await().toString();
      assertEquals("HTTP_1_1", protocol);
    }

    assertEquals(2, connections.size());
  }
}
