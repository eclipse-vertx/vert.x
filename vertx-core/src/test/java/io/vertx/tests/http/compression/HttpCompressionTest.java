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
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.Zstd;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpServerConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static io.vertx.core.http.HttpHeaders.ACCEPT_ENCODING;
import static io.vertx.core.http.HttpMethod.PUT;

@RunWith(Parameterized.class)
public abstract class HttpCompressionTest extends HttpCompressionTestBase {

  @Parameterized.Parameters(name = "{index}: algorithm = {0}")
  public static Collection<Object[]> data() {
    List<Object[]> list = new ArrayList<>();
    list.add(new Object[] { CompressionConfig.gzip(1) });
    list.add(new Object[] { CompressionConfig.gzip(6) });
    list.add(new Object[] { CompressionConfig.gzip(9) });
    if (Zstd.isAvailable()) {
      list.add(new Object[] { CompressionConfig.zstd() });
    }
    list.add(new Object[] { CompressionConfig.snappy() });
    list.add(new Object[] { CompressionConfig.brotli() });
    return list;
  }

  private final CompressionConfig compressionConfig;

  protected HttpCompressionTest(HttpConfig config, CompressionConfig compressionConfig) {
    super(config);
    this.compressionConfig = compressionConfig;
  }

  @Override
  protected final String encoding() {
    return compressionConfig.encoding;
  }

  @Override
  protected final MessageToByteEncoder<ByteBuf> encoder() {
    return compressionConfig.encoder.get();
  }

  @Override
  protected final Optional<HttpCompressionOptions> serverCompressionConfig() {
    return Optional.of(new HttpCompressionOptions().addCompressor(compressionConfig.compressor));
  }

  @Test
  public void testSkipEncoding() throws Exception {
    server.close();
    server = config.forServer().setCompression(new HttpCompressionOptions().addCompressor(CompressionConfig.gzip(6).compressor)).create(vertx);
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
    HttpServerConfig options = config.forServer();
    options.setCompression(serverCompressionConfig().get());
    server = options.create(vertx);
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
    server = config.forServer().setDecompressionSupported(true).create(vertx);
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
    client = config.forClient().setDecompressionSupported(true).create(vertx);
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
    HttpServer server = config.forServer().create(vertx);
    try {
      server.requestHandler(req -> {
        String acceptEncoding = req.headers().get(ACCEPT_ENCODING);
        assertTrue("Expects accept-encoding '" + acceptEncoding + "' to contain " + encoding(), acceptEncoding.contains(encoding()));
        req.response().end();
      });
      server.listen().await();
      client.close();
      client = config.forClient().setDecompressionSupported(true).create(vertx);
      client.request(new RequestOptions())
        .onComplete(onSuccess(req -> {
          req.send().onComplete(onSuccess(resp -> {
            resp.end().onComplete(onSuccess(v -> {
              testComplete();
            }));
          }));
        }));
      await();
    } finally {
      server.close().await();
    }
  }
}
