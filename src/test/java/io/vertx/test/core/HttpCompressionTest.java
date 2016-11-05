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

import io.netty.util.CharsetUtil;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import org.junit.Test;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpCompressionTest extends HttpTestBase {

    private static final String LARGE_HTML_STRING = "<!--?xml version=\"1.0\" encoding=\"ISO-8859-1\"?-->\n"
            + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" "
            + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"><head>\n"
            + "    <title>Apache Tomcat</title>\n"
            + "</head>\n"
            + '\n'
            + "<body>\n"
            + "<h1>It works !</h1>\n"
            + '\n'
            + "<p>If you're seeing this page via a web browser, it means you've setup Tomcat successfully."
            + " Congratulations!</p>\n"
            + " \n"
            + "<p>This is the default Tomcat home page."
            + " It can be found on the local filesystem at: <code>/var/lib/tomcat7/webapps/ROOT/index.html</code></p>\n"
            + '\n'
            + "<p>Tomcat7 veterans might be pleased to learn that this system instance of Tomcat is installed with"
            + " <code>CATALINA_HOME</code> in <code>/usr/share/tomcat7</code> and <code>CATALINA_BASE</code> in"
            + " <code>/var/lib/tomcat7</code>, following the rules from"
            + " <code>/usr/share/doc/tomcat7-common/RUNNING.txt.gz</code>.</p>\n"
            + '\n'
            + "<p>You might consider installing the following packages, if you haven't already done so:</p>\n"
            + '\n'
            + "<p><b>tomcat7-docs</b>: This package installs a web application that allows to browse the Tomcat 7"
            + " documentation locally. Once installed, you can access it by clicking <a href=\"docs/\">here</a>.</p>\n"
            + '\n'
            + "<p><b>tomcat7-examples</b>: This package installs a web application that allows to access the Tomcat"
            + " 7 Servlet and JSP examples. Once installed, you can access it by clicking"
            + " <a href=\"examples/\">here</a>.</p>\n"
            + '\n'
            + "<p><b>tomcat7-admin</b>: This package installs two web applications that can help managing this Tomcat"
            + " instance. Once installed, you can access the <a href=\"manager/html\">manager webapp</a> and"
            + " the <a href=\"host-manager/html\">host-manager webapp</a>.</p><p>\n"
            + '\n'
            + "</p><p>NOTE: For security reasons, using the manager webapp is restricted"
            + " to users with role \"manager\"."
            + " The host-manager webapp is restricted to users with role \"admin\". Users are "
            + "defined in <code>/etc/tomcat7/tomcat-users.xml</code>.</p>\n"
            + '\n'
            + '\n'
            + '\n'
            + "</body></html>";

    HttpServer serverWithMinCompressionLevel, serverWithMaxCompressionLevel = null;

    HttpClient client2, clientraw, clientraw2 = null;

    public void setUp() throws Exception {
        super.setUp();
        client = vertx.createHttpClient(new HttpClientOptions().setTryUseCompression(true));
        clientraw = vertx.createHttpClient(new HttpClientOptions().setTryUseCompression(false));

        HttpServerOptions serverOpts = new HttpServerOptions()
                .setPort(DEFAULT_HTTP_PORT)
                .setCompressionSupported(true);
        // server = vertx.createHttpServer();
        serverWithMinCompressionLevel = vertx.createHttpServer(serverOpts.setPort(DEFAULT_HTTP_PORT - 1).setCompressionLevel(1));
        serverWithMaxCompressionLevel = vertx.createHttpServer(serverOpts.setPort(DEFAULT_HTTP_PORT + 1).setCompressionLevel(6));
    }

    @Test
    public void testDefaultRequestHeaders() {
        Handler<HttpServerRequest> requestHandler = req -> {
            assertEquals(2, req.headers().size());
            //  assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
            assertNotNull(req.headers().get("Accept-Encoding"));
            req.response().end(Buffer.buffer(LARGE_HTML_STRING).toString(CharsetUtil.UTF_8));
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

    public static boolean minCompressionTestPassed = false;

    public void testMinCompression() {
        client.request(HttpMethod.GET, DEFAULT_HTTP_PORT - 1, DEFAULT_HTTP_HOST, "some-uri",
                resp -> {
                    resp.bodyHandler(responseBuffer -> {
                        String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
                        assertEquals(LARGE_HTML_STRING, responseBody);
                        minCompressionTestPassed = true;
                        terminateTestWhenAllPassed();
                    });
                }).end();
    }

    public static boolean maxCompressionTestPassed = false;

    public void testMaxCompression() {
        client.request(HttpMethod.GET, DEFAULT_HTTP_PORT + 1, DEFAULT_HTTP_HOST, "some-uri",
                resp -> {
                    resp.bodyHandler(responseBuffer -> {
                        String responseBody = responseBuffer.toString(CharsetUtil.UTF_8);
                        assertEquals(LARGE_HTML_STRING, responseBody);
                        maxCompressionTestPassed = true;
                        terminateTestWhenAllPassed();
                    });
                }).end();
    }

    public static Integer maxRawCompressionResponseByteCount = null;

    public void testRawMaxCompression() {
        clientraw.request(HttpMethod.GET, DEFAULT_HTTP_PORT + 1, DEFAULT_HTTP_HOST, "some-uri",
                resp -> {
                    resp.bodyHandler(responseBuffer -> {
                        String responseCompressedBody = responseBuffer.toString(CharsetUtil.UTF_8);
                        Integer responseByteCount = responseCompressedBody.getBytes(CharsetUtil.UTF_8).length;
                        //1606
                       // assertEquals((Integer)1606,responseByteCount);
                        // assertEquals(LARGE_HTML_STRING, responseBody);
                        maxRawCompressionResponseByteCount = responseByteCount;
                        terminateTestWhenAllPassed();
                    });
                }).putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP).end();
    }

    public static Integer minRawCompressionResponseByteCount = null;

    public void testRawMinCompression() {
        clientraw.request(HttpMethod.GET, DEFAULT_HTTP_PORT - 1, DEFAULT_HTTP_HOST, "some-uri",
                resp -> {
                    resp.bodyHandler(responseBuffer -> {
                        String responseCompressedBody = responseBuffer.toString(CharsetUtil.UTF_8);
                        Integer responseByteCount = responseCompressedBody.getBytes(CharsetUtil.UTF_8).length;
                       // assertEquals((Integer)1642,responseByteCount);
                        minRawCompressionResponseByteCount = responseByteCount;
                        terminateTestWhenAllPassed();
                    });
                }).putHeader(HttpHeaders.ACCEPT_ENCODING, HttpHeaders.DEFLATE_GZIP).end();
    }

    public void terminateTestWhenAllPassed() {
        if (maxCompressionTestPassed && minCompressionTestPassed 
                && minRawCompressionResponseByteCount!=null && maxRawCompressionResponseByteCount!=null) {
            assertTrue("Checking compression byte size difference", maxRawCompressionResponseByteCount>0 
                    && minRawCompressionResponseByteCount > maxRawCompressionResponseByteCount);
            testComplete();
        }
    }
}
