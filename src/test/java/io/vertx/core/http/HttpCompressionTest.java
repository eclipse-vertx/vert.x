/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.netty.util.CharsetUtil;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;

/**
 */
public class HttpCompressionTest extends HttpTestBase {

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

  private HttpServer serverWithMinCompressionLevel, serverWithMaxCompressionLevel,serverWithClientDeCompression = null;
  private HttpClient clientraw = null;

  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions().setTryUseCompression(true));
    clientraw = vertx.createHttpClient(new HttpClientOptions().setTryUseCompression(false));

    HttpServerOptions serverOpts = new HttpServerOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setCompressionSupported(true);
    // server = vertx.createHttpServer();
    serverWithMinCompressionLevel = vertx.createHttpServer(serverOpts.setPort(DEFAULT_HTTP_PORT - 1).setCompressionLevel(1));
    serverWithMaxCompressionLevel = vertx.createHttpServer(serverOpts.setPort(DEFAULT_HTTP_PORT + 1).setCompressionLevel(9));
    serverWithClientDeCompression = vertx.createHttpServer(serverOpts.setCompressionSupported(false));
  }

  @Test
  public void testSkipEncoding() throws Exception {
    serverWithMaxCompressionLevel.requestHandler(req -> {
      assertNotNull(req.headers().get("Accept-Encoding"));
      req.response()
        .putHeader(HttpHeaders.CONTENT_ENCODING, HttpHeaders.IDENTITY)
        .end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
    });
    startServer(serverWithMaxCompressionLevel);
    clientraw.get(DEFAULT_HTTP_PORT + 1, DEFAULT_HTTP_HOST, "some-uri",
      onSuccess(resp -> {
        resp.bodyHandler(responseBuffer -> {
          String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
          assertEquals(COMPRESS_TEST_STRING, responseBody);
          testComplete();
        });
      })).putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP).end();
    await();
  }

  @Test
  public void testDefaultRequestHeaders() {
    Handler<HttpServerRequest> requestHandler = req -> {
      assertEquals(2, req.headers().size());
      //  assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
      assertNotNull(req.headers().get("Accept-Encoding"));
      req.response().end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
    };

    serverWithMinCompressionLevel.requestHandler(requestHandler);
    serverWithMaxCompressionLevel.requestHandler(requestHandler);

    serverWithMinCompressionLevel.listen(onSuccess(serverReady -> {
      testMinCompression();
      testRawMinCompression();
    }));

    serverWithMaxCompressionLevel.listen(onSuccess(serverReady -> {
      testMaxCompression();
      testRawMaxCompression();
    }));

    await();
  }

  private static boolean minCompressionTestPassed = false;

  private void testMinCompression() {
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT - 1, DEFAULT_HTTP_HOST, "some-uri",
      onSuccess(resp -> {
          resp.bodyHandler(responseBuffer -> {
            String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
            assertEquals(COMPRESS_TEST_STRING, responseBody);
            minCompressionTestPassed = true;
            terminateTestWhenAllPassed();
          });
        })).end();
  }

  private static boolean maxCompressionTestPassed = false;

  private void testMaxCompression() {
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT + 1, DEFAULT_HTTP_HOST, "some-uri",
      onSuccess(resp -> {
          resp.bodyHandler(responseBuffer -> {
            String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
            assertEquals(COMPRESS_TEST_STRING, responseBody);
            maxCompressionTestPassed = true;
            terminateTestWhenAllPassed();
          });
        })).end();
  }

  private static Integer rawMaxCompressionResponseByteCount = null;

  private void testRawMaxCompression() {
    clientraw.request(HttpMethod.GET, DEFAULT_HTTP_PORT + 1, DEFAULT_HTTP_HOST, "some-uri",
      onSuccess(resp -> {
          resp.bodyHandler(responseBuffer -> {
            String responseCompressedBody = responseBuffer.toString(CharsetUtil.UTF_8);
            Integer responseByteCount = responseCompressedBody.getBytes(CharsetUtil.UTF_8).length;
            //1606
            // assertEquals((Integer)1606,responseByteCount);
            // assertEquals(LARGE_HTML_STRING, responseBody);
            rawMaxCompressionResponseByteCount = responseByteCount;
            terminateTestWhenAllPassed();
          });
        })).putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP).end();
  }

  private static Integer rawMinCompressionResponseByteCount = null;

  private void testRawMinCompression() {
    clientraw.request(HttpMethod.GET, DEFAULT_HTTP_PORT - 1, DEFAULT_HTTP_HOST, "some-uri",
      onSuccess(resp -> {
          resp.bodyHandler(responseBuffer -> {
            String responseCompressedBody = responseBuffer.toString(CharsetUtil.UTF_8);
            Integer responseByteCount = responseCompressedBody.getBytes(CharsetUtil.UTF_8).length;
            // assertEquals((Integer)1642,responseByteCount);
            rawMinCompressionResponseByteCount = responseByteCount;
            terminateTestWhenAllPassed();
          });
        })).putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP).end();
  }

  private void terminateTestWhenAllPassed() {
    if (maxCompressionTestPassed && minCompressionTestPassed
        && rawMinCompressionResponseByteCount != null && rawMaxCompressionResponseByteCount != null) {
      assertTrue("Checking compression byte size difference", rawMaxCompressionResponseByteCount > 0
          && rawMinCompressionResponseByteCount > rawMaxCompressionResponseByteCount);
      testComplete();
    }
  }

  @Test
  public void testClientDeCompression() throws Exception {
    String compressData = "Test Client DeCompression....";
    serverWithClientDeCompression.requestHandler(req -> {
      assertNotNull(req.headers().get("Accept-Encoding"));
      Deflater compress = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
      byte[] bytes = compressData.getBytes();
      compress.setInput(bytes);
      compress.finish();
      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
      byte[] buf = new byte[1024];
      byte[] output;
      while (!compress.finished()) {
        int i = compress.deflate(buf);
        bos.write(buf, 0, i);
      }
      output = bos.toByteArray();
      try {
        bos.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      compress.end();
      req.response()
        .putHeader(HttpHeaders.CONTENT_ENCODING, "deflate")
        .end(Buffer.buffer(output));
    });

    startServer(serverWithClientDeCompression);
    client.get(DEFAULT_HTTP_PORT+1, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI,
      onSuccess(resp -> {
        resp.bodyHandler(responseBuffer -> {
           assertEquals(compressData,responseBuffer.toString());
          testComplete();
        });
      })).putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP).end();
    await();
  }
}
