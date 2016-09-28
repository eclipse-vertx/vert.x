/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

/**
 * @author <a href="mailto:clonyara@gmail.com">Yan Gleyzer</a>
 */
public class HttpDecompressionTest extends HttpTestBase {

  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions());
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setDecompressionSupported(true));
  }

  @Test
  public void testDefaultRequestHeaders() throws Exception {
    String expected = TestUtils.randomAlphaString(1000);
    byte[] dataGzipped = TestUtils.compressGzip(expected);
    server.requestHandler(req -> {
      assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
      req.bodyHandler(buffer -> {
        assertEquals(expected, buffer.toString());
        req.response().end();
      });
    });

    server.listen(onSuccess(server -> {
      client
      .request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "some-uri", resp -> testComplete())
      .putHeader("Content-Encoding", "gzip")
      .end(Buffer.buffer(dataGzipped));
    }));

    await();
  }
}
