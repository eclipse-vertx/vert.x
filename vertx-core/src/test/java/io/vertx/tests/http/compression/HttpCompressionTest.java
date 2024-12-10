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

package io.vertx.tests.http.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Queue;
import java.util.function.Function;

import static io.vertx.core.http.HttpHeaders.ACCEPT_ENCODING;
import static io.vertx.core.http.HttpMethod.PUT;

public abstract class HttpCompressionTest extends HttpTestBase {

  protected static final String COMPRESS_TEST_STRING = "/*\n" +
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

  protected Buffer compressedTestString;

  public HttpCompressionTest() {
  }

  protected abstract String encoding();

  protected abstract MessageToByteEncoder<ByteBuf> encoder();

  protected void configureServerCompression(HttpServerOptions options) {
  }

  protected Buffer compress(Buffer src) {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addFirst(encoder());
    channel.writeAndFlush(Unpooled.copiedBuffer(((BufferInternal)src).getByteBuf()));
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
          assertNull(resp.getHeader(HttpHeaders.CONTENT_ENCODING));
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
    testServerStandardCompression(resp -> resp.end(Buffer.buffer(COMPRESS_TEST_STRING).toString(StandardCharsets.UTF_8)));
  }

  @Test
  public void testServerStandardCompressionSendFile() throws Exception {
    File f = File.createTempFile("vertx", ".txt");
    Files.write(f.toPath(), COMPRESS_TEST_STRING.getBytes(StandardCharsets.UTF_8));
    f.deleteOnExit();
    testServerStandardCompression(resp -> resp.sendFile(f.getAbsolutePath()));
  }

  private void testServerStandardCompression(Function<HttpServerResponse, Future<?>> sender) throws Exception {
    waitFor(2);
    server.close();
    HttpServerOptions options = createBaseServerOptions();
    configureServerCompression(options);
    server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      // assertEquals(2, req.headers().size());
      assertNotNull(req.headers().get(HttpHeaders.ACCEPT_ENCODING));
      sender.apply(req.response()).onComplete(onSuccess(v -> complete()));
    });
    startServer();
    client.request(new RequestOptions()
        .addHeader(HttpHeaders.ACCEPT_ENCODING, encoding()))
      .onComplete(onSuccess(req -> {
        req.send()
          .flatMap(HttpClientResponse::body)
          .onComplete(onSuccess(body -> {
            assertEquals(StringUtil.toHexString(compressedTestString.getBytes()), StringUtil.toHexString(body.getBytes()));
            complete();
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
    client = vertx.createHttpClient(createBaseClientOptions().setDecompressionSupported(true));
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
    client = vertx.createHttpClient(createBaseClientOptions().setDecompressionSupported(true));
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
