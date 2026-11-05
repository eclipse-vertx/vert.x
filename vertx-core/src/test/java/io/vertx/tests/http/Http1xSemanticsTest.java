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
package io.vertx.tests.http;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.test.http.HttpConfigurator;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class Http1xSemanticsTest extends HttpSemanticsTest {

  public Http1xSemanticsTest() {
    super(HttpConfigurator.Http1x.DEFAULT);
  }

  // Connection-specific headers are meaningful on HTTP/1.x and stay on the wire
  @Override
  protected void assertConnectionHeadersSanitized(MultiMap respHeaders) {
    assertEquals(HttpHeaders.CLOSE.toString(), respHeaders.get(HttpHeaders.CONNECTION));
    assertEquals("timeout=5", respHeaders.get(HttpHeaders.KEEP_ALIVE));
    assertEquals("websocket", respHeaders.get(HttpHeaders.UPGRADE));
    assertEquals("keep-alive", respHeaders.get("proxy-connection"));
  }

  // Use a raw socket to check the body response is effectively empty (it could be an empty chunk)
  protected MultiMap checkEmptyHttpResponse(HttpMethod method, int sc, MultiMap reqHeaders) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setStatusCode(sc);
      resp.headers().addAll(reqHeaders);
      resp.end();
    });
    startServer(testAddress);
    NetClient client = vertx.createNetClient();
    try {
      CompletableFuture<MultiMap> result = new CompletableFuture<>();
      client.connect(testAddress).onComplete(ar -> {
        if (ar.succeeded()) {
          NetSocket so = ar.result();
          so.write(
            method.name() + " / HTTP/1.1\r\n" +
              "Connection: close\r\n" +
              "\r\n");
          Buffer body = Buffer.buffer();
          so.exceptionHandler(result::completeExceptionally);
          so.handler(body::appendBuffer);
          so.endHandler(v -> {
            String content = body.toString();
            int idx = content.indexOf("\r\n\r\n");
            if (idx == content.length() - 4) {
              LinkedList<String> records = new LinkedList<>(Arrays.asList(content.substring(0, idx).split("\\r\\n")));
              String statusLine = records.removeFirst();
              assertEquals("HTTP/1.1 " + sc, statusLine.substring(0, statusLine.indexOf(' ', 9)));
              assertEquals("", content.substring(idx + 4));
              MultiMap respHeaders = HttpHeaders.headers();
              records.forEach(record -> {
                int index = record.indexOf(":");
                String value = record.substring(0, index);
                respHeaders.add(value.trim(), record.substring(index + 1).trim());
              });
              result.complete(respHeaders);
            } else {
              result.completeExceptionally(new Exception());
            }
          });
        } else {
          result.completeExceptionally(ar.cause());
        }
      });
      return result.get(20, TimeUnit.SECONDS);
    } finally {
      client.close();
    }
  }
}
