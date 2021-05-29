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

import io.netty.util.CharsetUtil;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

import org.junit.Test;

import static io.vertx.core.http.HttpMethod.PUT;


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

  private HttpServer serverWithMinCompressionLevel, serverWithMaxCompressionLevel,serverWithClientDeCompression,serverWithServerDecompression = null;
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
    serverWithClientDeCompression = vertx.createHttpServer(serverOpts.setPort(DEFAULT_HTTP_PORT + 2).setCompressionSupported(true));
    serverWithServerDecompression = vertx.createHttpServer(serverOpts.setPort(DEFAULT_HTTP_PORT - 2).setDecompressionSupported(true));
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
    clientraw.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT + 1)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("some-uri"))
      .onComplete(onSuccess(req -> {
        req.putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP);
        req.send(onSuccess(resp -> {
          assertNull(resp.getHeader(HttpHeaders.CONTENT_ENCODING));
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
    client.request(new RequestOptions().setPort(DEFAULT_HTTP_PORT - 1).setHost(DEFAULT_HTTP_HOST))
      .onComplete(onSuccess(req -> {
        req.send()
          .flatMap(HttpClientResponse::body)
          .onComplete(onSuccess(body -> {
            String responseBody = body.toString(CharsetUtil.UTF_8);
            assertEquals(COMPRESS_TEST_STRING, responseBody);
            minCompressionTestPassed = true;
            terminateTestWhenAllPassed();
          }));
      }));
  }

  private static boolean maxCompressionTestPassed = false;

  private void testMaxCompression() {
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT + 1)
      .setHost(DEFAULT_HTTP_HOST)
    ).onComplete(onSuccess(req -> {
      req
        .putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP)
        .send(onSuccess(resp -> {
          resp.bodyHandler(responseBuffer -> {
            String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
            assertEquals(COMPRESS_TEST_STRING, responseBody);
            maxCompressionTestPassed = true;
            terminateTestWhenAllPassed();
          });
      }));
    }));
  }

  private static Integer rawMaxCompressionResponseByteCount = null;

  private void testRawMaxCompression() {
    clientraw.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT + 1)
      .setHost(DEFAULT_HTTP_HOST)
    )
    .onComplete(onSuccess(req -> {
      req.putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP);
      req.send(onSuccess(resp -> {
        resp.body(onSuccess(responseBuffer -> {
          String responseCompressedBody = responseBuffer.toString(CharsetUtil.UTF_8);
          Integer responseByteCount = responseCompressedBody.getBytes(CharsetUtil.UTF_8).length;
          //1606
          // assertEquals((Integer)1606,responseByteCount);
          // assertEquals(LARGE_HTML_STRING, responseBody);
          rawMaxCompressionResponseByteCount = responseByteCount;
          terminateTestWhenAllPassed();
        }));
      }));
    }));
  }

  private static Integer rawMinCompressionResponseByteCount = null;

  private void testRawMinCompression() {
    clientraw.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT - 1)
      .setHost(DEFAULT_HTTP_HOST)
    )
      .onComplete(onSuccess(req -> {
        req
          .putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP)
          .send(onSuccess(resp -> {
          resp.bodyHandler(responseBuffer -> {
            String responseCompressedBody = responseBuffer.toString(CharsetUtil.UTF_8);
            Integer responseByteCount = responseCompressedBody.getBytes(CharsetUtil.UTF_8).length;
            // assertEquals((Integer)1642,responseByteCount);
            rawMinCompressionResponseByteCount = responseByteCount;
            terminateTestWhenAllPassed();
          });
        }));
      }));
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
      req.response()
        .end(compressData);
    });

    startServer(serverWithClientDeCompression);
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT + 2)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("some-uri"))
      .onComplete(onSuccess(req -> {
        req
          .putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP)
          .send(onSuccess(resp -> {
            resp.body(onSuccess(responseBuffer -> {
              assertEquals(compressData,responseBuffer.toString());
              testComplete();
            }));
          }));
      }));
    await();
  }

  @Test
  public void testServerDecompression() throws Exception {
    String data = "Test Server Decompression";
    serverWithServerDecompression.requestHandler(req -> {
      assertNotNull(req.headers().get("Accept-Encoding"));
      req.bodyHandler(buf -> {
        assertEquals(data,buf.toString());
        req.response().putHeader(HttpHeaders.CONTENT_ENCODING,HttpHeaders.DEFLATE_GZIP).end(buf);
      });
    });
    startServer(serverWithServerDecompression);
    client.request(new RequestOptions()
      .setMethod(PUT)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT - 2)
      .setURI(DEFAULT_TEST_URI)
    ).onComplete(onSuccess(req -> {
      req
        .putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP)
        .send(Buffer.buffer(data), onSuccess(resp -> {
          testComplete();
        }));
    }));
    await();
  }
}
