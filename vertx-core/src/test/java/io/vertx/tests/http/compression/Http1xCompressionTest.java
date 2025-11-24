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
package io.vertx.tests.http.compression;

import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpServerConfig;
import org.junit.Test;

public class Http1xCompressionTest extends HttpCompressionTest {

  public Http1xCompressionTest(CompressionConfig config) {
    super(HttpConfig.Http1x.DEFAULT, config);
  }

  @Test
  public void testServerCompressionBelowThreshold() throws Exception {
    // set compression threshold to be greater than the content string size so it WILL NOT be compressed
    HttpServerConfig httpServerOptions = config.forServer();
    httpServerOptions.setCompression(serverCompressionConfig().get().setContentSizeThreshold(COMPRESS_TEST_STRING.length() * 2));

    doTest(httpServerOptions, onSuccess(resp -> {
      // check content encoding header is not set
      assertNull(resp.getHeader(HttpHeaders.CONTENT_ENCODING));

      resp.body().onComplete(onSuccess(responseBuffer -> {
        // check that the response body bytes is itself
        String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
        assertEquals(COMPRESS_TEST_STRING, responseBody);
        testComplete();
      }));
    }));
  }

  @Test
  public void testServerCompressionAboveThreshold() throws Exception {
    // set compression threshold to be less than the content string size so it WILL be compressed
    HttpServerConfig httpServerOptions = config.forServer();
    httpServerOptions.setCompression(serverCompressionConfig().get().setContentSizeThreshold(COMPRESS_TEST_STRING.length() / 2));

    doTest(httpServerOptions, onSuccess(resp -> {
      // check content encoding header is set
      assertEquals(encoding(), resp.getHeader(HttpHeaders.CONTENT_ENCODING));

      resp.body().onComplete(onSuccess(responseBuffer -> {
        // check that response body bytes is compressed
        assertEquals(StringUtil.toHexString(compressedTestString.getBytes()), StringUtil.toHexString(responseBuffer.getBytes()));
        testComplete();
      }));
    }));
  }

  private void doTest(HttpServerConfig serverConfig, Handler<AsyncResult<HttpClientResponse>> handler) throws Exception {
    server.close();
    server = serverConfig.create(vertx);
    server.requestHandler(req -> {
      assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
      req.response()
        .end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
    });
    startServer();
    client.request(new RequestOptions())
      .onComplete(onSuccess(req -> {
        req.putHeader(HttpHeaders.ACCEPT_ENCODING, encoding());
        req.send().onComplete(handler);
      }));
    await();
  }
}
