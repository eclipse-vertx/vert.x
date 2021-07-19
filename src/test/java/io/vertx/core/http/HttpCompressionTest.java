/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.buffer.Buffer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;

import static io.vertx.core.http.HttpMethod.PUT;

@RunWith(Parameterized.class)
public abstract class HttpCompressionTest extends HttpTestBase {

  private static final String COMPRESS_TEST_STRING = "/*\n" +
      " * Copyright (c) 2011-2016 The original author or authors\n" +
      " * ------------------------------------------------------\n" +
      " * All rights reserved. This program and the accompanying materials\n" +
      " * are made available under the terms of the Eclipse Public License v1.0\n" +
      " * and Apache License v2.0 which accompanies this distribution.\n" +
      " *\n" +
      " *     The Eclipse Public License is available at\n" +
      " *     http://www.eclipse.org/legal/epl-v10.html\n" +
      " *\n" +
      " *     The Apache License v2.0 is available at\n" +
      " *     http://www.opensource.org/licenses/apache2.0.php\n" +
      " *\n" +
      " * You may elect to redistribute this code under either of these licenses.\n" +
      " */";

  @Parameterized.Parameters(name = "{index}: compressionLevel = {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      { 1 }, { 6 }, { 9 }
    });
  }

  private Buffer compressedTestString;
  private int compressionLevel;

  public HttpCompressionTest(int compressionLevel) {
    this.compressionLevel = compressionLevel;
  }

  public void setUp() throws Exception {
    super.setUp();

    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addFirst(new JdkZlibEncoder(ZlibWrapper.GZIP, compressionLevel));
    channel.writeAndFlush(Unpooled.copiedBuffer(COMPRESS_TEST_STRING, StandardCharsets.UTF_8));
    channel.close();
    Queue<Object> messages = channel.outboundMessages();
    compressedTestString = Buffer.buffer();
    ByteBuf buf;
    while ((buf = (ByteBuf) messages.poll()) != null) {
      byte[] tmp = new byte[buf.readableBytes()];
      buf.readBytes(tmp);
      buf.release();
      compressedTestString.appendBytes(tmp);
    }
  }

  @Test
  public void testSkipEncoding() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setCompressionSupported(true));
    server.requestHandler(req -> {
      assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
      req.response()
        .putHeader(HttpHeaders.CONTENT_ENCODING, HttpHeaders.IDENTITY)
        .end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
    });
    startServer();
    client.request(new RequestOptions())
      .onComplete(onSuccess(req -> {
        req.putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP);
        req.send(onSuccess(resp -> {
          if (req.version() != HttpVersion.HTTP_2) {
            assertNull(resp.getHeader(HttpHeaders.CONTENT_ENCODING));
          } else {
            assertEquals(HttpHeaders.IDENTITY.toString(), resp.getHeader(HttpHeaders.CONTENT_ENCODING));
          }
          resp.body(onSuccess(responseBuffer -> {
            String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
            assertEquals(COMPRESS_TEST_STRING, responseBody);
            testComplete();
          }));
        }));
      }));
    await();
  }

  @Test
  public void testServerCompression() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions()
      .setCompressionLevel(compressionLevel)
      .setCompressionSupported(true)
    );
    server.requestHandler(req -> {
      // assertEquals(2, req.headers().size());
      assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
      req.response().end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
    });
    startServer();
    client.request(new RequestOptions()
      .addHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP))
      .onComplete(onSuccess(req -> {
        req.send()
          .flatMap(HttpClientResponse::body)
          .onComplete(onSuccess(body -> {
            assertEquals(StringUtil.toHexString(compressedTestString.getBytes()), StringUtil.toHexString(body.getBytes()));
            testComplete();
          }));
      }));
    await();
  }

  @Test
  public void testServerDecompression() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setDecompressionSupported(true));
    server.requestHandler(req -> {
      req.body().onComplete(onSuccess(body -> {
        assertEquals(COMPRESS_TEST_STRING, body.toString());
        req.response().end();
      }));
    });
    startServer();
    client.request(new RequestOptions()
      .setMethod(PUT)
      .putHeader(HttpHeaders.CONTENT_ENCODING, "gzip"))
      .onComplete(onSuccess(req -> {
        req.send(compressedTestString)
          .flatMap(HttpClientResponse::body)
          .onComplete(onSuccess(body -> {
            testComplete();
          }));
      }));
    await();
  }

  @Test
  public void testClientDecompression() throws Exception {
    server.requestHandler(req -> {
      // assertEquals(2, req.headers().size());
      assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
      req.response().putHeader(HttpHeaders.CONTENT_ENCODING, "gzip").end(compressedTestString);
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setTryUseCompression(true));
    client.request(new RequestOptions())
      .onComplete(onSuccess(req -> {
        req.send()
          .flatMap(HttpClientResponse::body)
          .onComplete(onSuccess(body -> {
            assertEquals(COMPRESS_TEST_STRING, body.toString());
            testComplete();
          }));
      }));
    await();
  }
}
