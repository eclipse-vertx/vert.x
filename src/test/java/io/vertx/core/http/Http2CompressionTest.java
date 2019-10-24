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
import io.vertx.core.buffer.Buffer;
import org.junit.Test;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class Http2CompressionTest extends Http2TestBase {

    private static final String COMPRESS_TEST_STRING = "/*\n"
            + " * Copyright (c) 2011-2016 The original author or authors\n"
            + " * ------------------------------------------------------\n"
            + " * All rights reserved. This program and the accompanying materials\n"
            + " * are made available under the terms of the Eclipse Public License v1.0\n"
            + " * and Apache License v2.0 which accompanies this distribution.\n"
            + " *\n"
            + " *     The Eclipse Public License is available at\n"
            + " *     http://www.eclipse.org/legal/epl-v10.html\n"
            + " *\n"
            + " *     The Apache License v2.0 is available at\n"
            + " *     http://www.opensource.org/licenses/apache2.0.php\n"
            + " *\n"
            + " * You may elect to redistribute this code under either of these licenses.\n"
            + " */";

    HttpServer serverWithMinCompressionLevel, serverWithMaxCompressionLevel, serverWithCompressionWithoutTls = null;

    HttpClient clientraw = null, clearTextClient = null;

    public void setUp() throws Exception {
        super.setUp();

        client = vertx.createHttpClient(createHttp2ClientOptions().setTryUseCompression(true));
        clientraw = vertx.createHttpClient(createHttp2ClientOptions().setTryUseCompression(false));
        clearTextClient = vertx.createHttpClient(new HttpClientOptions().setTryUseCompression(true).setProtocolVersion(HttpVersion.HTTP_2).setHttp2ClearTextUpgrade(true));

        // server = vertx.createHttpServer();
        serverWithMinCompressionLevel = vertx.createHttpServer(
                createHttp2ServerOptions(DEFAULT_HTTP_PORT - 1, DEFAULT_HTTP_HOST)
                        .setCompressionSupported(true)
                        .setCompressionLevel(1));
        serverWithMaxCompressionLevel = vertx.createHttpServer(
                createHttp2ServerOptions(DEFAULT_HTTP_PORT + 1, DEFAULT_HTTP_HOST)
                        .setCompressionSupported(true)
                        .setCompressionLevel(9));
        //regression part of test for https://github.com/eclipse-vertx/vert.x/issues/2934
        serverWithCompressionWithoutTls = vertx.createHttpServer(
                new HttpServerOptions()
                        .setCompressionSupported(true)
                        .setPort(DEFAULT_HTTP_PORT + 2)
                        .setHost(DEFAULT_HTTP_HOST));
    }

    @Test
    public void testDefaultRequestHeaders() {
        Handler<HttpServerRequest> requestHandler = req -> {
            //    assertEquals(2, req.headers().size());
            assertEquals(HttpVersion.HTTP_2, req.version());
            //  assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
            assertNotNull(req.headers().get("Accept-Encoding"));
            req.response().end(Buffer.buffer(COMPRESS_TEST_STRING).toString(CharsetUtil.UTF_8));
        };

        serverWithMinCompressionLevel.requestHandler(requestHandler);
        serverWithMaxCompressionLevel.requestHandler(requestHandler);
        serverWithCompressionWithoutTls.requestHandler(requestHandler);
        serverWithMinCompressionLevel.listen(onSuccess(serverReady -> {
            testMinCompression();
            testRawMinCompression();
        }));

        serverWithMaxCompressionLevel.listen(onSuccess(serverReady -> {
            testMaxCompression();
            testRawMaxCompression();
        }));

        serverWithCompressionWithoutTls.listen(onSuccess(serverReady -> {
            testCompressionWithHttp2Upgrade();
        }));

        await();
    }

    public static boolean minCompressionTestPassed = false;

    public void testMinCompression() {
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

    public static boolean compressionWithoutTlsPassed = false;

    public void testCompressionWithHttp2Upgrade() {
        clearTextClient.request(HttpMethod.GET, DEFAULT_HTTP_PORT + 2, DEFAULT_HTTP_HOST, "some-uri",
                onSuccess(resp -> {
                    resp.bodyHandler(responseBuffer -> {
                        String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
                        assertEquals(COMPRESS_TEST_STRING, responseBody);
                        compressionWithoutTlsPassed = true;
                        terminateTestWhenAllPassed();
                    });
                })).end();
    }

    public static boolean maxCompressionTestPassed = false;

    public void testMaxCompression() {
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

    public static Integer rawMaxCompressionResponseByteCount = null;

    public void testRawMaxCompression() {
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

    public static Integer rawMinCompressionResponseByteCount = null;

    public void testRawMinCompression() {
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

    public void terminateTestWhenAllPassed() {
        if (maxCompressionTestPassed && minCompressionTestPassed && compressionWithoutTlsPassed
                && rawMinCompressionResponseByteCount != null && rawMaxCompressionResponseByteCount != null) {
            assertTrue("Checking compression byte size difference", rawMaxCompressionResponseByteCount > 0
                    && rawMinCompressionResponseByteCount > rawMaxCompressionResponseByteCount);
            testComplete();
        }
    }
}
