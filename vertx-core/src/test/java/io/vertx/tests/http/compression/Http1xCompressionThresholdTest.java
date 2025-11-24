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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpServerConfig;
import org.junit.Test;

import java.util.Optional;

public class Http1xCompressionThresholdTest extends HttpCompressionTestBase {

  public Http1xCompressionThresholdTest() {
    super(HttpConfig.Http1x.DEFAULT);
  }

  @Override
  protected String encoding() {
    return "gzip";
  }

  @Override
  protected MessageToByteEncoder<ByteBuf> encoder() {
    return new JdkZlibEncoder(ZlibWrapper.GZIP, 6);
  }

  @Override
  protected Optional<HttpCompressionOptions> serverCompressionConfig() {
    GzipOptions compressor = StandardCompressionOptions.gzip(6, StandardCompressionOptions.gzip().windowBits(), StandardCompressionOptions.gzip().memLevel());
    return Optional.of(new HttpCompressionOptions().addCompressor(compressor));
  }

  @Test
  public void testServerCompressionBelowThreshold() throws Exception {
    // set compression threshold to be greater than the content string size so it WILL NOT be compressed
    HttpServerConfig httpServerOptions = config.forServer();
    httpServerOptions.setCompressionSupported(true);
    httpServerOptions.setCompression(new HttpCompressionOptions()
      .addCompressor(CompressionConfig.gzip(6).compressor)
      .setContentSizeThreshold(COMPRESS_TEST_STRING.length() * 2)
    );

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
    HttpServerConfig config = this.config.forServer();
    config.setCompressionSupported(true);
    config.setCompression(new HttpCompressionOptions()
      .addCompressor(CompressionConfig.gzip(6).compressor)
      .setContentSizeThreshold(COMPRESS_TEST_STRING.length() / 2)
    );

    doTest(config, onSuccess(resp -> {
      // check content encoding header is set
      assertEquals(encoding(), resp.getHeader(HttpHeaders.CONTENT_ENCODING));

      resp.body().onComplete(onSuccess(responseBuffer -> {
        // check that response body bytes is compressed
        assertEquals(StringUtil.toHexString(compressedTestString.getBytes()), StringUtil.toHexString(responseBuffer.getBytes()));
        testComplete();
      }));
    }));
  }

  private void doTest(HttpServerConfig config, Handler<AsyncResult<HttpClientResponse>> handler) throws Exception {
    HttpServer server = config.create(vertx);
    try {
      server.requestHandler(req -> {
        assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
        req.response()
          .end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
      });
      server.listen().await();
      client.request(new RequestOptions())
        .onComplete(onSuccess(req -> {
          req.putHeader(HttpHeaders.ACCEPT_ENCODING, encoding());
          req.send().onComplete(handler);
        }));
      await();
    } finally {
      server.close().await();
    }
  }
}
