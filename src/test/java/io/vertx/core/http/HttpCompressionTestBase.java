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
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;

import java.util.Queue;

import static io.vertx.core.http.HttpHeaders.ACCEPT_ENCODING;
import static io.vertx.core.http.HttpHeaders.CONTENT_ENCODING;
import static io.vertx.core.http.HttpMethod.PUT;

public abstract class HttpCompressionTestBase extends HttpTestBase {

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

  private Buffer compressedTestString;

  public HttpCompressionTestBase() {
  }

  protected abstract String encoding();

  protected abstract MessageToByteEncoder<ByteBuf> encoder();

  protected void configureServerCompression(HttpServerOptions options) {
  }

  protected Buffer compress(Buffer src) {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addFirst(encoder());
    channel.writeAndFlush(Unpooled.copiedBuffer(src.getByteBuf()));
    channel.close();
    Queue<Object> messages = channel.outboundMessages();
    Buffer dst = Buffer.buffer();
    ByteBuf buf;
    while ((buf = (ByteBuf) messages.poll()) != null) {
      byte[] tmp = new byte[buf.readableBytes()];
      buf.readBytes(tmp);
      buf.release();
      dst.appendBytes(tmp);
    }
    return dst;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    compressedTestString = compress(Buffer.buffer(COMPRESS_TEST_STRING));
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
        req.putHeader(HttpHeaders.ACCEPT_ENCODING, encoding());
        req.send().onComplete(onSuccess(resp -> {
          if (req.version() != HttpVersion.HTTP_2) {
            assertNull(resp.getHeader(HttpHeaders.CONTENT_ENCODING));
          } else {
            assertEquals(HttpHeaders.IDENTITY.toString(), resp.getHeader(HttpHeaders.CONTENT_ENCODING));
          }
          resp.body().onComplete(onSuccess(responseBuffer -> {
            String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
            assertEquals(COMPRESS_TEST_STRING, responseBody);
            testComplete();
          }));
        }));
      }));
    await();
  }

  @Test
  public void testServerStandardCompression() throws Exception {
    server.close();
    HttpServerOptions options = createBaseServerOptions();
    configureServerCompression(options);
    server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      // assertEquals(2, req.headers().size());
      assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
      req.response().end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
    });
    startServer();
    client.request(new RequestOptions()
      .addHeader(HttpHeaders.ACCEPT_ENCODING, encoding()))
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
      .putHeader(HttpHeaders.CONTENT_ENCODING, encoding()))
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
      req.response().putHeader(HttpHeaders.CONTENT_ENCODING, encoding()).end(compressedTestString);
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

  @Test
  public void testClientAcceptEncoding() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions());
    server.requestHandler(req -> {
      String acceptEncoding = req.headers().get(ACCEPT_ENCODING);
      assertTrue("Expects accept-encoding '" + acceptEncoding + "' to contain " + encoding(), acceptEncoding.contains(encoding()));
      req.response().end();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setTryUseCompression(true));
    client.request(new RequestOptions())
      .onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.end().onComplete(onSuccess(v -> {
            testComplete();
          }));
        }));
      }));
    await();
  }
}
