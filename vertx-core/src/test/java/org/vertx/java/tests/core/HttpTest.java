/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.tests.core;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.*;
import org.vertx.java.core.http.impl.HttpHeadersAdapter;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.net.NetClientOptions;
import org.vertx.java.core.net.NetServerOptions;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.SocketDefaults;
import org.vertx.java.core.streams.Pump;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.vertx.java.tests.core.TestUtils.*;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpTest extends HttpTestBase {

  private File testDir;

  @Before
  public void before() throws Exception {
    testDir = Files.createTempDirectory("vertx-test").toFile();
    testDir.deleteOnExit();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST));
    client = vertx.createHttpClient(new HttpClientOptions());
  }

  @Test
  public void testClientOptions() {
    HttpClientOptions options = new HttpClientOptions();

    assertEquals(-1, options.getSendBufferSize());
    int rand = randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    try {
      options.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setSendBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertEquals(-1, options.getReceiveBufferSize());
    rand = randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    try {
      options.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setReceiveBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(-1, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    try {
      options.setTrafficClass(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setTrafficClass(256);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = SocketDefaults.instance.isTcpKeepAlive();
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = SocketDefaults.instance.getSoLinger();
    assertEquals(soLinger, options.getSoLinger());
    rand = randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    try {
      options.setSoLinger(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyStorePath());
    String randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePath(randString));
    assertEquals(randString, options.getKeyStorePath());

    assertNull(options.getKeyStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePassword(randString));
    assertEquals(randString, options.getKeyStorePassword());

    assertNull(options.getTrustStorePath());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePath(randString));
    assertEquals(randString, options.getTrustStorePath());

    assertNull(options.getTrustStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePassword(randString));
    assertEquals(randString, options.getTrustStorePassword());

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
    assertTrue(options.isTrustAll());

    assertTrue(options.isVerifyHost());
    assertEquals(options, options.setVerifyHost(false));
    assertFalse(options.isVerifyHost());

    assertEquals(5, options.getMaxPoolSize());
    rand = randomPositiveInt();
    assertEquals(options, options.setMaxPoolSize(rand));
    assertEquals(rand, options.getMaxPoolSize());
    try {
      options.setMaxPoolSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setMaxPoolSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isKeepAlive());
    assertEquals(options, options.setKeepAlive(false));
    assertFalse(options.isKeepAlive());

    assertFalse(options.isPipelining());
    assertEquals(options, options.setPipelining(true));
    assertTrue(options.isPipelining());

    assertEquals(60000, options.getConnectTimeout());
    rand = randomPositiveInt();
    assertEquals(options, options.setConnectTimeout(rand));
    assertEquals(rand, options.getConnectTimeout());
    try {
      options.setConnectTimeout(-2);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isTryUseCompression());
    assertEquals(options, options.setTryUseCompression(true));
    assertEquals(true, options.isTryUseCompression());

    testComplete();
  }


  @Test
  public void testServerOptions() {
    HttpServerOptions options = new HttpServerOptions();

    assertEquals(-1, options.getSendBufferSize());
    int rand = randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    try {
      options.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setSendBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertEquals(-1, options.getReceiveBufferSize());
    rand = randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    try {
      options.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setReceiveBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(-1, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    try {
      options.setTrafficClass(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setTrafficClass(256);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = SocketDefaults.instance.isTcpKeepAlive();
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = SocketDefaults.instance.getSoLinger();
    assertEquals(soLinger, options.getSoLinger());
    rand = randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    try {
      options.setSoLinger(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyStorePath());
    String randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePath(randString));
    assertEquals(randString, options.getKeyStorePath());

    assertNull(options.getKeyStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePassword(randString));
    assertEquals(randString, options.getKeyStorePassword());

    assertNull(options.getTrustStorePath());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePath(randString));
    assertEquals(randString, options.getTrustStorePath());

    assertNull(options.getTrustStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePassword(randString));
    assertEquals(randString, options.getTrustStorePassword());

    assertEquals(1024, options.getAcceptBacklog());
    rand = randomPositiveInt();
    assertEquals(options, options.setAcceptBacklog(rand));
    assertEquals(rand, options.getAcceptBacklog());

    assertFalse(options.isCompressionSupported());
    assertEquals(options, options.setCompressionSupported(true));
    assertTrue(options.isCompressionSupported());

    assertEquals(65536, options.getMaxWebsocketFrameSize());
    rand = randomPositiveInt();
    assertEquals(options, options.setMaxWebsocketFrameSize(rand));
    assertEquals(rand, options.getMaxWebsocketFrameSize());

    assertEquals(80, options.getPort());
    rand = randomPositiveInt();
    assertEquals(options, options.setPort(rand));
    assertEquals(rand, options.getPort());

    assertEquals("0.0.0.0", options.getHost());
    randString = randomUnicodeString(100);
    assertEquals(options, options.setHost(randString));
    assertEquals(randString, options.getHost());


    testComplete();
  }

  @Test
  public void testServerChaining() {
    server.requestHandler(req -> {
      assertTrue(req.response().setChunked(true) == req.response());
      assertTrue(req.response().write("foo", "UTF-8") == req.response());
      assertTrue(req.response().write("foo") == req.response());
      testComplete();
    });

    server.listen(onSuccess(server -> {
      client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testServerChainingSendFile() throws Exception {
    File file = setupFile("test-server-chaining.dat", "blah");
    server.requestHandler(req -> {
      assertTrue(req.response().sendFile(file.getAbsolutePath()) == req.response());
      file.delete();
      testComplete();
    });

    server.listen(onSuccess(server -> {
      client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testClientChaining() {
    server.requestHandler(noOpHandler());

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
      assertTrue(req.setChunked(true) == req);
      assertTrue(req.sendHead() == req);
      assertTrue(req.write("foo", "UTF-8") == req);
      assertTrue(req.write("foo") == req);
      assertTrue(req.write(new Buffer("foo")) == req);
      testComplete();
    }));

    await();
  }

  @Test
  public void testLowerCaseHeaders() {
    server.requestHandler(req -> {
      assertEquals("foo", req.headers().get("Foo"));
      assertEquals("foo", req.headers().get("foo"));
      assertEquals("foo", req.headers().get("fOO"));
      assertTrue(req.headers().contains("Foo"));
      assertTrue(req.headers().contains("foo"));
      assertTrue(req.headers().contains("fOO"));

      req.response().putHeader("Quux", "quux");

      assertEquals("quux", req.response().headers().get("Quux"));
      assertEquals("quux", req.response().headers().get("quux"));
      assertEquals("quux", req.response().headers().get("qUUX"));
      assertTrue(req.response().headers().contains("Quux"));
      assertTrue(req.response().headers().contains("quux"));
      assertTrue(req.response().headers().contains("qUUX"));

      req.response().end();
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals("quux", resp.headers().get("Quux"));
        assertEquals("quux", resp.headers().get("quux"));
        assertEquals("quux", resp.headers().get("qUUX"));
        assertTrue(resp.headers().contains("Quux"));
        assertTrue(resp.headers().contains("quux"));
        assertTrue(resp.headers().contains("qUUX"));
        testComplete();
      });

      req.putHeader("Foo", "foo");
      assertEquals("foo", req.headers().get("Foo"));
      assertEquals("foo", req.headers().get("foo"));
      assertEquals("foo", req.headers().get("fOO"));
      assertTrue(req.headers().contains("Foo"));
      assertTrue(req.headers().contains("foo"));
      assertTrue(req.headers().contains("fOO"));

      req.end();
    }));

    await();
  }

  @Test
  public void testHeadersOnRequestOptions() {
    server.requestHandler(req -> {
      assertEquals("bar", req.headers().get("foo"));
      req.response().end();
    });
    server.listen(onSuccess(server -> {
      MultiMap headers = new CaseInsensitiveMultiMap();
      headers.add("foo", "bar");
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI).setHeaders(headers), resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testPutHeadersOnRequestOptions() {
    server.requestHandler(req -> {
      assertEquals("bar", req.headers().get("foo"));
      req.response().end();
    });
    server.listen(onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI).putHeader("foo", "bar"), resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testSimpleGET() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "GET", client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimplePUT() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PUT", client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimplePOST() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "POST", client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleDELETE() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "DELETE", client.delete(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleHEAD() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "HEAD", client.head(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleTRACE() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "TRACE", client.trace(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleCONNECT() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "CONNECT", client.connect(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleOPTIONS() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "OPTIONS", client.options(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimplePATCH() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PATCH", client.patch(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleGETNonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "GET", resp -> testComplete());
  }

  @Test
  public void testSimplePUTNonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PUT", resp -> testComplete());
  }

  @Test
  public void testSimplePOSTNonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "POST", resp -> testComplete());
  }

  @Test
  public void testSimpleDELETENonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "DELETE", resp -> testComplete());
  }

  @Test
  public void testSimpleHEADNonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "HEAD", resp -> testComplete());
  }

  @Test
  public void testSimpleTRACENonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "TRACE", resp -> testComplete());
  }

  @Test
  public void testSimpleCONNECTNonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "CONNECT", resp -> testComplete());
  }

  @Test
  public void testSimpleOPTIONSNonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "OPTIONS", resp -> testComplete());
  }

  @Test
  public void testSimplePATCHNonSpecific() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PATCH", resp -> testComplete());
  }

  private void testSimpleRequest(String uri, String method, Handler<HttpClientResponse> handler) {
    testSimpleRequest(uri, method, client.request(method, new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), handler));
  }

  private void testSimpleRequest(String uri, String method, HttpClientRequest request) {
    String path = uri.indexOf('?') == -1 ? uri : uri.substring(0, uri.indexOf('?'));
    server.requestHandler(req -> {
      assertEquals(path, req.path());
      assertEquals(method, req.method());
      req.response().end();
    });

    server.listen(onSuccess(server -> request.end()));

    await();
  }

  @Test
  public void testAbsoluteURI() {
    testURIAndPath("http://localhost:" + DEFAULT_HTTP_PORT + "/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
  }

  @Test
  public void testRelativeURI() {
    testURIAndPath("/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
  }

  private void testURIAndPath(String uri, String path) {
    server.requestHandler(req -> {
      assertEquals(uri, req.uri());
      assertEquals(path, req.path());
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete());
    }));

    await();
  }

  @Test
  public void testParamsAmpersand() {
    testParams('&');
  }

  @Test
  public void testParamsSemiColon() {
    testParams(';');
  }

  private void testParams(char delim) {
    Map<String, String> params = genMap(10);
    String query = generateQueryString(params, delim);

    server.requestHandler(req -> {
      assertEquals(query, req.query());
      assertEquals(params.size(), req.params().size());
      for (Map.Entry<String, String> entry : req.params()) {
        assertEquals(entry.getValue(), params.get(entry.getKey()));
      }
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("some-uri/?" + query), resp -> testComplete());
    }));

    await();
  }

  @Test
  public void testNoParams() {
    server.requestHandler(req -> {
      assertNull(req.query());
      assertTrue(req.params().isEmpty());
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
    }));

    await();
  }

  @Test
  public void testDefaultRequestHeaders() {
    server.requestHandler(req -> {
      assertEquals(1, req.headers().size());
      assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
    }));

    await();
  }

  @Test
  public void testRequestHeadersPutAll() {
    testRequestHeaders(false);
  }

  @Test
  public void testRequestHeadersIndividually() {
    testRequestHeaders(true);
  }

  private void testRequestHeaders(boolean individually) {
    MultiMap headers = getHeaders(10);

    server.requestHandler(req -> {
      assertEquals(headers.size() + 1, req.headers().size());
      for (Map.Entry<String, String> entry : headers) {
        assertEquals(entry.getValue(), req.headers().get(entry.getKey()));
      }
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
      if (individually) {
        for (Map.Entry<String, String> header : headers) {
          req.headers().add(header.getKey(), header.getValue());
        }
      } else {
        req.headers().set(headers);
      }
      req.end();
    }));

    await();
  }

  @Test
  public void testResponseHeadersPutAll() {
    testResponseHeaders(false);
  }

  @Test
  public void testResponseHeadersIndividually() {
    testResponseHeaders(true);
  }

  private void testResponseHeaders(boolean individually) {
    MultiMap headers = getHeaders(10);

    server.requestHandler(req -> {
      if (individually) {
        for (Map.Entry<String, String> header : headers) {
          req.response().headers().add(header.getKey(), header.getValue());
        }
      } else {
        req.response().headers().set(headers);
      }
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(headers.size() + 1, resp.headers().size());
        for (Map.Entry<String, String> entry : headers) {
          assertEquals(entry.getValue(), resp.headers().get(entry.getKey()));
        }
        testComplete();
      });
    }));

    await();
  }

  @Test
  public void testResponseMultipleSetCookieInHeader() {
    testResponseMultipleSetCookie(true, false);
  }

  @Test
  public void testResponseMultipleSetCookieInTrailer() {
    testResponseMultipleSetCookie(false, true);
  }

  @Test
  public void testResponseMultipleSetCookieInHeaderAndTrailer() {
    testResponseMultipleSetCookie(true, true);
  }

  private void testResponseMultipleSetCookie(boolean inHeader, boolean inTrailer) {
    List<String> cookies = new ArrayList<>();

    server.requestHandler(req -> {
      if (inHeader) {
        List<String> headers = new ArrayList<>();
        headers.add("h1=h1v1");
        headers.add("h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
        cookies.addAll(headers);
        req.response().headers().set("Set-Cookie", headers);
      }
      if (inTrailer) {
        req.response().setChunked(true);
        List<String> trailers = new ArrayList<>();
        trailers.add("t1=t1v1");
        trailers.add("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
        cookies.addAll(trailers);
        req.response().trailers().set("Set-Cookie", trailers);
      }
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.endHandler(v -> {
          assertEquals(cookies.size(), resp.cookies().size());
          for (int i = 0; i < cookies.size(); ++i) {
            assertEquals(cookies.get(i), resp.cookies().get(i));
          }
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void testUseRequestAfterComplete() {
    server.requestHandler(noOpHandler());

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
      req.end();

      Buffer buff = new Buffer();
      try {
        req.end();
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.continueHandler(noOpHandler());
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.drainHandler(noOpHandler());
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.end("foo");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.end(buff);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.end("foo", "UTF-8");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.exceptionHandler(noOpHandler());
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.sendHead();
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.setChunked(false);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.setWriteQueueMaxSize(123);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.write(buff);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.write("foo");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.write("foo", "UTF-8");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.write(buff);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        req.writeQueueFull();
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }

      testComplete();
    }));

    await();
  }

  @Test
  public void testRequestBodyBufferAtEnd() {
    Buffer body = randomBuffer(1000);
    server.requestHandler(req -> req.bodyHandler(buffer -> {
      assertEquals(body, buffer);
      req.response().end();
    }));

    server.listen(onSuccess(server -> {
      client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete()).end(body);
    }));

    await();
  }

  @Test
  public void testRequestBodyStringDefaultEncodingAtEnd() {
    testRequestBodyStringAtEnd(null);
  }

  @Test
  public void testRequestBodyStringUTF8AtEnd() {
    testRequestBodyStringAtEnd("UTF-8");
  }

  @Test
  public void testRequestBodyStringUTF16AtEnd() {
    testRequestBodyStringAtEnd("UTF-16");
  }

  private void testRequestBodyStringAtEnd(String encoding) {
    String body = randomUnicodeString(1000);
    Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(bodyBuff, buffer);
        testComplete();
      });
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
      if (encoding == null) {
        req.end(body);
      } else {
        req.end(body, encoding);
      }
    }));

    await();
  }

  @Test
  public void testRequestBodyWriteBufferChunked() {
    testRequestBodyWriteBuffer(true);
  }

  @Test
  public void testRequestBodyWriteBufferNonChunked() {
    testRequestBodyWriteBuffer(false);
  }

  private void testRequestBodyWriteBuffer(boolean chunked) {
    Buffer body = new Buffer();

    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(body, buffer);
        req.response().end();
      });
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
      int numWrites = 10;
      int chunkSize = 100;

      if (chunked) {
        req.setChunked(true);
      } else {
        req.headers().set("Content-Length", String.valueOf(numWrites * chunkSize));
      }
      for (int i = 0; i < numWrites; i++) {
        Buffer b = randomBuffer(chunkSize);
        body.appendBuffer(b);
        req.write(b);
      }
      req.end();
    }));

    await();
  }

  @Test
  public void testRequestBodyWriteStringChunkedDefaultEncoding() {
    testRequestBodyWriteString(true, null);
  }

  @Test
  public void testRequestBodyWriteStringChunkedUTF8() {
    testRequestBodyWriteString(true, "UTF-8");
  }

  @Test
  public void testRequestBodyWriteStringChunkedUTF16() {
    testRequestBodyWriteString(true, "UTF-16");
  }

  @Test
  public void testRequestBodyWriteStringNonChunkedDefaultEncoding() {
    testRequestBodyWriteString(false, null);
  }

  @Test
  public void testRequestBodyWriteStringNonChunkedUTF8() {
    testRequestBodyWriteString(false, "UTF-8");
  }

  @Test
  public void testRequestBodyWriteStringNonChunkedUTF16() {
    testRequestBodyWriteString(false, "UTF-16");
  }

  private void testRequestBodyWriteString(boolean chunked, String encoding) {
    String body = randomUnicodeString(1000);
    Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        assertEquals(bodyBuff, buff);
        testComplete();
      });
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());

      if (chunked) {
        req.setChunked(true);
      } else {
        req.headers().set("Content-Length", String.valueOf(bodyBuff.length()));
      }

      if (encoding == null) {
        req.write(body);
      } else {
        req.write(body, encoding);
      }
      req.end();
    }));

    await();
  }

  @Test
  public void testRequestWriteBuffer() {
    Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        assertEquals(body, buff);
        testComplete();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
      req.setChunked(true);
      req.write(body);
      req.end();
    }));

    await();
  }

  @Test
  public void testDefaultStatus() {
    testStatusCode(-1, null);
  }

  @Test
  public void testOtherStatus() {
    // Doesn't really matter which one we choose
    testStatusCode(405, null);
  }

  @Test
  public void testStatusMessage() {
    testStatusCode(404, "some message");
  }

  private void testStatusCode(int code, String statusMessage) {
    server.requestHandler(req -> {
      if (code != -1) {
        req.response().setStatusCode(code);
      }
      if (statusMessage != null) {
        req.response().setStatusMessage(statusMessage);
      }
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        if (code != -1) {
          assertEquals(code, resp.statusCode());
        } else {
          assertEquals(200, resp.statusCode());
        }
        if (statusMessage != null) {
          assertEquals(statusMessage, resp.statusMessage());
        }
        testComplete();
      });
    }));

    await();
  }

  @Test
  public void testResponseTrailersPutAll() {
    testResponseTrailers(false);
  }

  @Test
  public void testResponseTrailersPutIndividually() {
    testResponseTrailers(true);
  }

  private void testResponseTrailers(boolean individually) {
    MultiMap trailers = getHeaders(10);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      if (individually) {
        for (Map.Entry<String, String> header : trailers) {
          req.response().trailers().add(header.getKey(), header.getValue());
        }
      } else {
        req.response().trailers().set(trailers);
      }
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.endHandler(v -> {
          assertEquals(trailers.size(), resp.trailers().size());
          for (Map.Entry<String, String> entry : trailers) {
            assertEquals(entry.getValue(), resp.trailers().get(entry.getKey()));
          }
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void testResponseNoTrailers() {
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.endHandler(v -> {
          assertTrue(resp.trailers().isEmpty());
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void testUseResponseAfterComplete() {
    server.requestHandler(req -> {
      Buffer buff = new Buffer();
      HttpServerResponse resp = req.response();
      resp.end();

      try {
        resp.drainHandler(noOpHandler());
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }

      try {
        resp.end();
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.end("foo");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.end(buff);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.end("foo", "UTF-8");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.exceptionHandler(noOpHandler());
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.setChunked(false);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.setWriteQueueMaxSize(123);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.write(buff);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.write("foo");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
      try {
        resp.write("foo", "UTF-8");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }

      try {
        resp.write(buff);
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }

      try {
        resp.writeQueueFull();
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }

      try {
        resp.sendFile("asokdasokd");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }

      testComplete();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
    }));

    await();
  }

  @Test
  public void testResponseBodyBufferAtEnd() {
    Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().end(body);
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(body, buff);
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void testResponseBodyStringDefaultEncodingAtEnd() {
    testResponseBodyStringAtEnd(null);
  }

  @Test
  public void testResponseBodyStringUTF8AtEnd() {
    testResponseBodyStringAtEnd("UTF-8");
  }

  @Test
  public void testResponseBodyStringUTF16AtEnd() {
    testResponseBodyStringAtEnd("UTF-16");
  }

  private void testResponseBodyStringAtEnd(String encoding) {
    String body = randomUnicodeString(1000);
    Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    server.requestHandler(req -> {
      if (encoding == null) {
        req.response().end(body);
      } else {
        req.response().end(body, encoding);
      }
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(bodyBuff, buff);
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void testResponseBodyWriteStringNonChunked() {
    server.requestHandler(req -> {
      try {
        req.response().write("foo");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
        testComplete();
      }
    });

    server.listen(onSuccess(s -> {
      client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testResponseBodyWriteBufferChunked() {
    testResponseBodyWriteBuffer(true);
  }

  @Test
  public void testResponseBodyWriteBufferNonChunked() {
    testResponseBodyWriteBuffer(false);
  }

  private void testResponseBodyWriteBuffer(boolean chunked) {
    Buffer body = new Buffer();

    int numWrites = 10;
    int chunkSize = 100;

    server.requestHandler(req -> {
      if (chunked) {
        req.response().setChunked(true);
      } else {
        req.response().headers().set("Content-Length", String.valueOf(numWrites * chunkSize));
      }

      for (int i = 0; i < numWrites; i++) {
        Buffer b = randomBuffer(chunkSize);
        body.appendBuffer(b);
        req.response().write(b);
      }
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(body, buff);
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void testResponseBodyWriteStringChunkedDefaultEncoding() {
    testResponseBodyWriteString(true, null);
  }

  @Test
  public void testResponseBodyWriteStringChunkedUTF8() {
    testResponseBodyWriteString(true, "UTF-8");
  }

  @Test
  public void testResponseBodyWriteStringChunkedUTF16() {
    testResponseBodyWriteString(true, "UTF-16");
  }

  @Test
  public void testResponseBodyWriteStringNonChunkedDefaultEncoding() {
    testResponseBodyWriteString(false, null);
  }

  @Test
  public void testResponseBodyWriteStringNonChunkedUTF8() {
    testResponseBodyWriteString(false, "UTF-8");
  }

  @Test
  public void testResponseBodyWriteStringNonChunkedUTF16() {
    testResponseBodyWriteString(false, "UTF-16");
  }

  private void testResponseBodyWriteString(boolean chunked, String encoding) {
    String body = randomUnicodeString(1000);
    Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = new Buffer(body);
    } else {
      bodyBuff = new Buffer(body, encoding);
    }

    server.requestHandler(req -> {
      if (chunked) {
        req.response().setChunked(true);
      } else {
        req.response().headers().set("Content-Length", String.valueOf(bodyBuff.length()));
      }
      if (encoding == null) {
        req.response().write(body);
      } else {
        req.response().write(body, encoding);
      }
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(bodyBuff, buff);
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void testResponseWriteBuffer() {
    Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(body, buff);
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testPipeliningOrder() {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true).setPipelining(true).setMaxPoolSize(1));
    int requests = 100;

    AtomicInteger reqCount = new AtomicInteger(0);
    server.requestHandler(req -> {
      int theCount = reqCount.get();
      assertEquals(theCount, Integer.parseInt(req.headers().get("count")));
      reqCount.incrementAndGet();
      req.response().setChunked(true);
      req.bodyHandler(buff -> {
        assertEquals("This is content " + theCount, buff.toString());
        //We write the response back after a random time to increase the chances of responses written in the
        //wrong order if we didn't implement pipelining correctly
        vertx.setTimer(1 + (long) (10 * Math.random()), id -> {
          req.response().headers().set("count", String.valueOf(theCount));
          req.response().write(buff);
          req.response().end();
        });
      });
    });

    server.listen(onSuccess(s -> {
      for (int count = 0; count < requests; count++) {
        int theCount = count;
        HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
          assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
          resp.bodyHandler(buff -> {
            assertEquals("This is content " + theCount, buff.toString());
            if (theCount == requests - 1) {
              testComplete();
            }
          });
        });
        req.setChunked(true);
        req.headers().set("count", String.valueOf(count));
        req.write("This is content " + count);
        req.end();
      }
    }));

    await();
  }

  @Test
  public void testKeepAlive() throws Exception {
    testKeepAlive(true, 5, 10, 5);
  }

  @Test
  public void testNoKeepAlive() throws Exception {
    testKeepAlive(false, 5, 10, 10);
  }

  private void testKeepAlive(boolean keepAlive, int poolSize, int numServers, int expectedConnectedServers) throws Exception {
    client.close();
    server.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(keepAlive).setPipelining(false).setMaxPoolSize(poolSize));
    int requests = 100;

    // Start the servers
    HttpServer[] servers = new HttpServer[numServers];
    CountDownLatch startServerLatch = new CountDownLatch(numServers);
    Set<HttpServer> connectedServers = new ConcurrentHashSet<>();
    for (int i = 0; i < numServers; i++) {
      HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT));
      server.requestHandler(req -> {
        connectedServers.add(server);
        req.response().end();
      });
      server.listen(ar -> {
        assertTrue(ar.succeeded());
        startServerLatch.countDown();
      });
      servers[i] = server;
    }

    awaitLatch(startServerLatch);

    CountDownLatch reqLatch = new CountDownLatch(requests);
    for (int count = 0; count < requests; count++) {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(200, resp.statusCode());
        reqLatch.countDown();
      });
    }

    awaitLatch(reqLatch);

    assertEquals(expectedConnectedServers, connectedServers.size());

    CountDownLatch serverCloseLatch = new CountDownLatch(numServers);
    for (HttpServer server: servers) {
      server.close(ar -> {
        assertTrue(ar.succeeded());
        serverCloseLatch.countDown();
      });
    }

    awaitLatch(serverCloseLatch);
  }

  @Test
  public void testSendFile() throws Exception {
    String content = randomUnicodeString(10000);
    sendFile("test-send-file.html", content, null, false);
  }

  @Test
  public void testSendFileWithHandler() throws Exception {
    String content = randomUnicodeString(10000);
    sendFile("test-send-file.html", content, null, true);
  }

  @Test
  public void testFileNotFound() throws Exception {
    sendFile(null, "<html><body>Resource not found</body><html>", null, false);
  }

  @Test
  public void testSendFileNotFoundWith404Page() throws Exception {
    String content = "<html><body>This is my 404 page</body></html>";
    sendFile(null, content, "my-404-page.html", false);
  }

  @Test
  public void testSendFileNotFoundWith404PageAndHandler() throws Exception {
    String content = "<html><body>This is my 404 page</body></html>";
    sendFile(null, content, "my-404-page.html", true);
  }

  private void sendFile(String sendFile, String contentExpected, String notFoundFile, boolean handler) throws Exception {
    File fileToDelete;
    if (sendFile != null) {
      fileToDelete = setupFile(sendFile, contentExpected);
    } else if (notFoundFile != null) {
      fileToDelete = setupFile(notFoundFile, contentExpected);
    } else {
      fileToDelete = null;
    }

    CountDownLatch latch;
    if (handler) {
      latch = new CountDownLatch(2);
    } else {
      latch = new CountDownLatch(1);
    }

    server.requestHandler(req -> {
      if (handler) {
        Handler<AsyncResult<Void>> doneHandler = onSuccess(v -> latch.countDown());
        if (sendFile != null) { // Send file with handler
          req.response().sendFile(fileToDelete.getAbsolutePath(), doneHandler);
        } else if (notFoundFile != null) { // File doesn't exist, send not found resource with handler
          req.response().sendFile("doesnotexist.html", fileToDelete.getAbsolutePath(), doneHandler);
        } else { // File doesn't exist, send default not found resource with handler
          req.response().sendFile("doesnotexist.html", doneHandler);
        }
      } else {
        if (sendFile != null) { // Send file
          req.response().sendFile(fileToDelete.getAbsolutePath());
        } else if (notFoundFile != null) { // File doesn't exist, send not found resource
          req.response().sendFile("doesnotexist.html", fileToDelete.getAbsolutePath());
        } else { // File doesn't exist, send default not found resource
          req.response().sendFile("doesnotexist.html");
        }
      }
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        if (sendFile != null) {
          assertEquals(200, resp.statusCode());
        } else {
          assertEquals(404, resp.statusCode());
        }
        assertEquals("text/html", resp.headers().get("Content-Type"));
        resp.bodyHandler(buff -> {
          assertEquals(contentExpected, buff.toString());
          if (fileToDelete != null) {
            assertEquals(fileToDelete.length(), Long.parseLong(resp.headers().get("content-length")));
            fileToDelete.delete();
          }
          latch.countDown();
        });
      });
    }));

    assertTrue("Timed out waiting for test to complete.", latch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testSendFileOverrideHeaders() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile("test-send-file.html", content);

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(file.getAbsolutePath());
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(file.length(), Long.parseLong(resp.headers().get("content-length")));
        assertEquals("wibble", resp.headers().get("content-type"));
        resp.bodyHandler(buff -> {
          assertEquals(content, buff.toString());
          file.delete();
          testComplete();
        });
      });
    }));

    await();
  }

  @Test
  public void test100ContinueDefault() throws Exception {
    Buffer toSend = randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.endHandler(v -> testComplete());
      });
      req.headers().set("Expect", "100-continue");
      req.setChunked(true);
      req.continueHandler(v -> {
        req.write(toSend);
        req.end();
      });
      req.sendHead();
    }));

    await();
  }

  @Test
  public void test100ContinueHandled() throws Exception {
    Buffer toSend = randomBuffer(1000);
    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "100 Continue");
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.endHandler(v -> testComplete());
      });
      req.headers().set("Expect", "100-continue");
      req.setChunked(true);
      req.continueHandler(v -> {
        req.write(toSend);
        req.end();
      });
      req.sendHead();
    }));

    await();
  }

  @Test
  public void testClientDrainHandler() {
    pausingServer(s -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
      req.setChunked(true);
      assertFalse(req.writeQueueFull());
      req.setWriteQueueMaxSize(1000);
      Buffer buff = randomBuffer(10000);
      vertx.setPeriodic(1, id -> {
        req.write(buff);
        if (req.writeQueueFull()) {
          vertx.cancelTimer(id);
          req.drainHandler(v -> {
            assertFalse(req.writeQueueFull());
            testComplete();
          });

          // Tell the server to resume
          vertx.eventBus().send("server_resume", "");
        }
      });
    });

    await();
  }

  @Test
  public void testServerDrainHandler() {
    drainingServer(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.pause();
        Handler<Message<Buffer>> resumeHandler = msg -> resp.resume();
        vertx.eventBus().registerHandler("client_resume", resumeHandler);
        resp.endHandler(v -> vertx.eventBus().unregisterHandler("client_resume", resumeHandler));
      });
    });

    await();
  }

  @Test
  public void testPoolingKeepAliveAndPipelining() {
    testPooling(true, true);
  }

  @Test
  public void testPoolingKeepAliveNoPipelining() {
    testPooling(true, false);
  }

  @Test
  public void testPoolingNoKeepAliveNoPipelining() {
    testPooling(false, false);
  }

  @Test
  public void testPoolingNoKeepAliveAndPipelining() {
    testPooling(false, true);
  }

  private void testPooling(boolean keepAlive, boolean pipelining) {
    String path = "foo.txt";
    int numGets = 100;
    int maxPoolSize = 10;
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(keepAlive).setPipelining(pipelining).setMaxPoolSize(maxPoolSize));

    server.requestHandler(req -> {
      String cnt = req.headers().get("count");
      req.response().headers().set("count", cnt);
      req.response().end();
    });

    AtomicBoolean completeAlready = new AtomicBoolean();

    server.listen(onSuccess(s -> {

      AtomicInteger cnt = new AtomicInteger(0);
      for (int i = 0; i < numGets; i++) {
        int theCount = i;
        HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(path), resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
          if (cnt.incrementAndGet() == numGets) {
            testComplete();
          }
        });
        req.exceptionHandler(t -> {
          if (pipelining && !keepAlive) {
            // Illegal combination - should get exception
            assertTrue(t instanceof IllegalStateException);
            if (completeAlready.compareAndSet(false, true)) {
              testComplete();
            }
          } else {
            fail("Should not throw exception: " + t.getMessage());
          }
        });
        req.headers().set("count", String.valueOf(i));
        req.end();
      }
    }));

    await();
  }

  @Test
  public void testConnectionErrorsGetReportedToRequest() {
    AtomicInteger clientExceptions = new AtomicInteger();
    AtomicInteger req2Exceptions = new AtomicInteger();
    AtomicInteger req3Exceptions = new AtomicInteger();

    Handler<String> checkEndHandler = name -> {
      if (clientExceptions.get() == 1 && req2Exceptions.get() == 1 && req3Exceptions.get() == 1) {
        testComplete();
      }
    };

    client.exceptionHandler(t -> {
      assertEquals("More than more call to client exception handler was not expected", 1, clientExceptions.incrementAndGet());
      checkEndHandler.handle("Client");
    });

    // This one should cause an error in the Client Exception handler, because it has no exception handler set specifically.
    HttpClientRequest req1 = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(9998).setRequestURI("someurl1"), resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });
    // No exception handler set on request!

    HttpClientRequest req2 = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(9998).setRequestURI("someurl2"), resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });

    req2.exceptionHandler(t -> {
      assertEquals("More than more call to req2 exception handler was not expected", 1, req2Exceptions.incrementAndGet());
      checkEndHandler.handle("Request2");
    });

    HttpClientRequest req3 = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(9998).setRequestURI("someurl2"), resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });

    req3.exceptionHandler(t -> {
      assertEquals("More than more call to req2 exception handler was not expected", 1, req3Exceptions.incrementAndGet());
      checkEndHandler.handle("Request3");
    });

    req1.end();
    req2.end();
    req3.end();

    await();
  }

  @Test
  public void testRequestTimesoutWhenIndicatedPeriodExpiresWithoutAResponseFromRemoteServer() {
    server.requestHandler(noOpHandler()); // No response handler so timeout triggers

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("timeoutTest"), resp -> {
        fail("End should not be called because the request should timeout");
      });
      req.exceptionHandler(t -> {
        assertTrue("Expected to end with timeout exception but ended with other exception: " + t, t instanceof TimeoutException);
        testComplete();
      });
      req.setTimeout(1000);
      req.end();
    }));

    await();
  }

  @Test
  public void testRequestTimeoutExtendedWhenResponseChunksReceived() {
    long timeout = 2000;
    int numChunks = 100;
    AtomicInteger count = new AtomicInteger(0);
    long interval = timeout * 2 / numChunks;

    server.requestHandler(req -> {
      req.response().setChunked(true);
      vertx.setPeriodic(interval, timerID -> {
        req.response().write("foo");
        if (count.incrementAndGet() == numChunks) {
          req.response().end();
          vertx.cancelTimer(timerID);
        }
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("timeoutTest"), resp -> {
        assertEquals(200, resp.statusCode());
        resp.endHandler(v -> testComplete());
      });
      req.exceptionHandler(t -> fail("Should not be called"));
      req.setTimeout(timeout);
      req.end();
    }));

    await();
  }

  @Test
  public void testRequestTimeoutCanceledWhenRequestHasAnOtherError() {
    AtomicReference<Throwable> exception = new AtomicReference<>();
    // There is no server running, should fail to connect
    HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("timeoutTest"), resp -> {
      fail("End should not be called because the request should fail to connect");
    });
    req.exceptionHandler(exception::set);
    req.setTimeout(800);
    req.end();

    vertx.setTimer(1500, id -> {
      assertNotNull("Expected an exception to be set", exception.get());
      assertFalse("Expected to not end with timeout exception, but did: " + exception.get(), exception.get() instanceof TimeoutException);
      testComplete();
    });

    await();
  }

  @Test
  public void testRequestTimeoutCanceledWhenRequestEndsNormally() {
    server.requestHandler(req -> req.response().end());

    server.listen(onSuccess(s -> {
      AtomicReference<Throwable> exception = new AtomicReference<>();

      // There is no server running, should fail to connect
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("timeoutTest"), noOpHandler());
      req.exceptionHandler(exception::set);
      req.setTimeout(500);
      req.end();

      vertx.setTimer(1000, id -> {
        assertNull("Did not expect any exception", exception.get());
        testComplete();
      });
    }));

    await();
  }

  @Test
  public void testRequestNotReceivedIfTimedout() {
    server.requestHandler(req -> {
      vertx.setTimer(500, id -> {
        req.response().setStatusCode(200);
        req.response().end("OK");
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("timeoutTest"), resp -> fail("Response should not be handled"));
      req.exceptionHandler(t -> {
        assertTrue("Expected to end with timeout exception but ended with other exception: " + t, t instanceof TimeoutException);
        //Delay a bit to let any response come back
        vertx.setTimer(500, id -> testComplete());
      });
      req.setTimeout(100);
      req.end();
    }));

    await();
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(false, false, true, false, false, true, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(false, true, true, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(false, false, true, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(true, true, true, true, false, false, true);
  }

  @Test
  //Client specifies cert and it's not required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(true, true, true, true, true, false, true);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(false, true, true, true, true, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(true, true, true, false, true, false, false);
  }

  private void testTLS(boolean clientCert, boolean clientTrust,
                       boolean serverCert, boolean serverTrust,
                       boolean requireClientAuth, boolean clientTrustAll,
                       boolean shouldPass) throws Exception {
    client.close();
    server.close();
    HttpClientOptions options = new HttpClientOptions();
    options.setSsl(true);
    if (clientTrustAll) {
      options.setTrustAll(true);
    }
    if (clientTrust) {
      options.setTrustStorePath(findFileOnClasspath("tls/client-truststore.jks")).setTrustStorePassword("wibble");
    }
    if (clientCert) {
      options.setKeyStorePath(findFileOnClasspath("tls/client-keystore.jks")).setKeyStorePassword("wibble");
    }
    client = vertx.createHttpClient(options);
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setSsl(true);
    if (serverTrust) {
      serverOptions.setTrustStorePath(findFileOnClasspath("tls/server-truststore.jks")).setTrustStorePassword("wibble");
    }
    if (serverCert) {
      serverOptions.setKeyStorePath(findFileOnClasspath("tls/server-keystore.jks")).setKeyStorePassword("wibble");
    }
    if (requireClientAuth) {
      serverOptions.setClientAuthRequired(true);
    }
    server = vertx.createHttpServer(serverOptions.setPort(4043));
    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals("foo", buffer.toString());
        req.response().end("bar");
      });
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());

      client.exceptionHandler(t -> {
        if (shouldPass) {
          fail("Should not throw exception");
        } else {
          testComplete();
        }
      });
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(4043).setRequestURI(DEFAULT_TEST_URI), response -> {
        response.bodyHandler(data -> assertEquals("bar", data.toString()));
        testComplete();
      });
      req.end("foo");
    });
    await();
  }

  @Test
  public void testConnectInvalidPort() {
    client.exceptionHandler(t -> testComplete());
    client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(9998).setRequestURI(DEFAULT_TEST_URI), resp -> fail("Connect should not be called"));

    await();
  }

  @Test
  public void testConnectInvalidHost() {
    client.exceptionHandler(t -> testComplete());
    client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setHost("127.0.0.2").setRequestURI(DEFAULT_TEST_URI), resp -> fail("Connect should not be called"));

    await();
  }

  @Test
  public void testSetHandlersAfterListening() throws Exception {
    server.requestHandler(noOpHandler());

    server.listen(onSuccess(s -> {
      try {
        server.requestHandler(noOpHandler());
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //Ok
      }
      try {
        server.websocketHandler(noOpHandler());
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //Ok
      }
      testComplete();
    }));

    await();
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {
    client.close();
    server.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false));
    int numServers = 5;
    int numRequests = numServers * 100;

    List<HttpServer> servers = new ArrayList<>();
    Set<HttpServer> connectedServers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Map<HttpServer, Integer> requestCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numRequests);
    for (int i = 0; i < numServers; i++) {
      HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
      servers.add(theServer);
      theServer.requestHandler(req -> {
        connectedServers.add(theServer);
        Integer cnt = requestCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        requestCount.put(theServer, icnt);
        latchConns.countDown();
        req.response().end();
      }).listen(onSuccess(s -> latchListen.countDown()));
    }
    assertTrue(latchListen.await(10, TimeUnit.SECONDS));


    // Create a bunch of connections
    CountDownLatch latchClient = new CountDownLatch(numRequests);
    for (int i = 0; i < numRequests; i++) {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), res -> latchClient.countDown());
    }

    assertTrue(latchClient.await(10, TimeUnit.SECONDS));
    assertTrue(latchConns.await(10, TimeUnit.SECONDS));

    assertEquals(numServers, connectedServers.size());
    for (HttpServer server : servers) {
      assertTrue(connectedServers.contains(server));
    }
    assertEquals(numServers, requestCount.size());
    for (int cnt : requestCount.values()) {
      assertEquals(numRequests / numServers, cnt);
    }

    CountDownLatch closeLatch = new CountDownLatch(numServers);

    for (HttpServer server : servers) {
      server.close(ar -> {
        assertTrue(ar.succeeded());
        closeLatch.countDown();
      });
    }

    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    // Have a server running on a different port to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(8081));
    theServer.requestHandler(req -> {
      fail("Should not process request");
    }).listen(onSuccess(s -> latch.countDown()));
    awaitLatch(latch);

    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    theServer.requestHandler(req -> {
      fail("Should not process request");
    }).listen(onSuccess(s -> latch.countDown()));
    awaitLatch(latch);

    CountDownLatch closeLatch = new CountDownLatch(1);
    theServer.close(ar -> {
      assertTrue(ar.succeeded());
      closeLatch.countDown();
    });
    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    testSharedServersRoundRobin();
  }

  @Test
  public void testHeadNoBody() {
    server.requestHandler(req -> {
      assertEquals("HEAD", req.method());
      // Head never contains a body but it can contain a Content-Length header
      // Since headers from HEAD must correspond EXACTLY with corresponding headers for GET
      req.response().headers().set("Content-Length", String.valueOf(41));
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.head(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(41, Integer.parseInt(resp.headers().get("Content-Length")));
        resp.endHandler(v -> testComplete());
      }).end();
    }));

    await();
  }

  @Test
  public void testRemoteAddress() {
    server.requestHandler(req -> {
      assertEquals("127.0.0.1", req.remoteAddress().getHostAddress());
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testGetAbsoluteURI() {
    server.requestHandler(req -> {
      assertEquals("http://localhost:" + DEFAULT_HTTP_PORT + "/foo/bar", req.absoluteURI().toString());
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("/foo/bar"), resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testListenInvalidPort() {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(1128371831));
    server.requestHandler(noOpHandler()).listen(onFailure(server -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testListenInvalidHost() {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost("iqwjdoqiwjdoiqwdiojwd"));
    server.requestHandler(noOpHandler());
    server.listen(onFailure(s -> testComplete()));
  }

  @Test
  public void testPauseClientResponse() {
    int numWrites = 10;
    int numBytes = 100;
    server.requestHandler(req -> {
      req.response().setChunked(true);
      // Send back a big response in several chunks
      for (int i = 0; i < numWrites; i++) {
        req.response().write(randomBuffer(numBytes));
      }
      req.response().end();
    });

    AtomicBoolean paused = new AtomicBoolean();
    Buffer totBuff = new Buffer();
    HttpClientRequest clientRequest = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
      resp.pause();
      paused.set(true);
      resp.dataHandler(chunk -> {
        if (paused.get()) {
          fail("Shouldn't receive chunks when paused");
        } else {
          totBuff.appendBuffer(chunk);
        }
      });
      resp.endHandler(v -> {
        if (paused.get()) {
          fail("Shouldn't receive chunks when paused");
        } else {
          assertEquals(numWrites * numBytes, totBuff.length());
          testComplete();
        }
      });
      vertx.setTimer(500, id -> {
        paused.set(false);
        resp.resume();
      });
    });

    server.listen(onSuccess(s -> clientRequest.end()));

    await();
  }

  @Test
  public void testHttpVersion() {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testFormUploadFile() throws Exception {
    AtomicInteger attributeCount = new AtomicInteger();
    String content = "Vert.x rocks!";

    server.requestHandler(req -> {
      if (req.method().equals("POST")) {
        assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.expectMultiPart(true);
        req.uploadHandler(upload -> {
          upload.dataHandler(buffer -> {
            assertEquals(content, buffer.toString("UTF-8"));
          });
          assertEquals("file", upload.name());
          assertEquals("tmp-0.txt", upload.filename());
          assertEquals("image/gif", upload.contentType());
          upload.endHandler(v -> {
            assertEquals(content.length(), upload.size());
          });
        });
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          req.response().end();
        });
      }
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("/form"), resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(0, attributeCount.get());
        testComplete();
      });

      String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
      Buffer buffer = new Buffer();
      String body =
        "--" + boundary + "\r\n" +
          "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
          "Content-Type: image/gif\r\n" +
          "\r\n" +
          content + "\r\n" +
          "--" + boundary + "--\r\n";

      buffer.appendString(body);
      req.headers().set("content-length", String.valueOf(buffer.length()));
      req.headers().set("content-type", "multipart/form-data; boundary=" + boundary);
      req.write(buffer).end();
    }));

    await();
  }

  @Test
  public void testFormUploadAttributes() throws Exception {
    AtomicInteger attributeCount = new AtomicInteger();
    server.requestHandler(req -> {
      if (req.method().equals("POST")) {
        assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.expectMultiPart(true);
        req.uploadHandler(upload -> upload.dataHandler(buffer -> {
          fail("Should get here");
        }));
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          assertEquals("vert x", attrs.get("framework"));
          assertEquals("jvm", attrs.get("runson"));
          req.response().end();
        });
      }
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("/form"), resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(2, attributeCount.get());
        testComplete();
      });
      try {
        Buffer buffer = new Buffer();
        // Make sure we have one param that needs url encoding
        buffer.appendString("framework=" + URLEncoder.encode("vert x", "UTF-8") + "&runson=jvm", "UTF-8");
        req.headers().set("content-length", String.valueOf(buffer.length()));
        req.headers().set("content-type", "application/x-www-form-urlencoded");
        req.write(buffer).end();
      } catch (UnsupportedEncodingException e) {
        fail(e.getMessage());
      }
    }));

    await();
  }

  @Test
  public void testFormUploadAttributes2() throws Exception {
    AtomicInteger attributeCount = new AtomicInteger();
    server.requestHandler(req -> {
      if (req.method().equals("POST")) {
        assertEquals(req.path(), "/form");
        req.expectMultiPart(true);
        req.uploadHandler(event -> event.dataHandler(buffer -> {
          fail("Should not get here");
        }));
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          assertEquals("junit-testUserAlias", attrs.get("origin"));
          assertEquals("admin@foo.bar", attrs.get("login"));
          assertEquals("admin", attrs.get("pass word"));
          req.response().end();
        });
      }
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("/form"), resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(3, attributeCount.get());
        testComplete();
      });
      Buffer buffer = new Buffer();
      buffer.appendString("origin=junit-testUserAlias&login=admin%40foo.bar&pass+word=admin");
      req.headers().set("content-length", String.valueOf(buffer.length()));
      req.headers().set("content-type", "application/x-www-form-urlencoded");
      req.write(buffer).end();
    }));

    await();
  }

  @Test
  public void testAccessNetSocket() throws Exception {
    Buffer toSend = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "101 Upgrade");
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.endHandler(v -> {
          assertNotNull(resp.netSocket());
          testComplete();
        });
      });
      req.headers().set("content-length", String.valueOf(toSend.length()));
      req.write(toSend);
    }));

    await();
  }

  @Test
  public void testHostHeaderOverridePossible() {
    server.requestHandler(req -> {
      assertEquals("localhost:4444", req.headers().get("Host"));
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
      req.putHeader("Host", "localhost:4444");
      req.end();
    }));

    await();
  }

  @Test
  public void testResponseBodyWriteFixedString() {
    String body = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
    Buffer bodyBuff = new Buffer(body);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        resp.bodyHandler(buff -> {
          assertTrue(buffersEqual(bodyBuff, buff));
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testHttpConnect() {
    Buffer buffer = randomBuffer(128);
    Buffer received = new Buffer();
    vertx.createNetServer(new NetServerOptions().setPort(1235)).connectHandler(socket -> {
      socket.dataHandler(socket::write);
    }).listen(onSuccess(netServer -> {
      server.requestHandler(req -> {
        vertx.createNetClient(new NetClientOptions()).connect(netServer.actualPort(), onSuccess(socket -> {
          req.response().setStatusCode(200);
          req.response().setStatusMessage("Connection established");
          req.response().end();

          // Create pumps which echo stuff
          Pump.createPump(req.netSocket(), socket).start();
          Pump.createPump(socket, req.netSocket()).start();
          req.netSocket().closeHandler(v -> socket.close());
        }));
      });
      server.listen(onSuccess(s -> {
        client.connect(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
          assertEquals(200, resp.statusCode());
          NetSocket socket = resp.netSocket();
          socket.dataHandler(buff -> {
            received.appendBuffer(buff);
            if (received.length() == buffer.length()) {
              netServer.close();
              assertTrue(buffersEqual(buffer, received));
              testComplete();
            }
          });
          socket.write(buffer);
        }).end();
      }));
    }));

    await();
  }

  @Test
  public void testRequestsTimeoutInQueue() {

    server.requestHandler(req -> {
      vertx.setTimer(1000, id -> {
        req.response().end();
      });
    });

    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false).setMaxPoolSize(1));

    server.listen(onSuccess(s -> {
      // Add a few requests that should all timeout
      for (int i = 0; i < 5; i++) {
        HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
          fail("Should not be called");
        });
        req.exceptionHandler(t -> assertTrue(t instanceof TimeoutException));
        req.setTimeout(500);
        req.end();
      }
      // Now another request that should not timeout
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
      req.exceptionHandler(t -> fail("Should not throw exception"));
      req.setTimeout(3000);
      req.end();
    }));

    await();
  }

  @Test
  public void testSendFileDirectory() {
    File file = new File(testDir, "testdirectory");
    server.requestHandler(req -> {
      vertx.fileSystem().mkdir(file.getAbsolutePath(), onSuccess(v -> {
        req.response().sendFile(file.getAbsolutePath());
      }));
    });

    server.listen(onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(403, resp.statusCode());
        vertx.fileSystem().delete(file.getAbsolutePath(), v -> testComplete());
      });
    }));

    await();
  }

  @Test
  public void testServerOptionsCopiedBeforeUse() {
    server.close();
    HttpServerOptions options = new HttpServerOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT);
    HttpServer server = vertx.createHttpServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(DEFAULT_HTTP_PORT + 1);
    server.requestHandler(req -> {
      req.response().end();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.getNow(new RequestOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setRequestURI("/uri"), res -> {
        assertEquals(200, res.statusCode());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse() {
    client.close();
    server.requestHandler(req -> {
      req.response().end();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      HttpClientOptions options = new HttpClientOptions();
      client = vertx.createHttpClient(options);
      // Now change something - but server should ignore this
      options.setSsl(true);
      client.getNow(new RequestOptions().setHost(DEFAULT_HTTP_HOST).setPort(DEFAULT_HTTP_PORT).setRequestURI("/uri"), res -> {
        assertEquals(200, res.statusCode());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testRequestOptions() {
    RequestOptions options = new RequestOptions();
    assertEquals(80, options.getPort());
    assertEquals(options, options.setPort(1234));
    assertEquals(1234, options.getPort());
    try {
      options.setPort(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setPort(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setPort(65536);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals("localhost", options.getHost());
    String randString = randomUnicodeString(100);
    assertEquals(options, options.setHost(randString));
    assertEquals(randString, options.getHost());
    MultiMap headers = new CaseInsensitiveMultiMap();
    assertNull(options.getHeaders());
    assertEquals(options, options.setHeaders(headers));
    assertSame(headers, options.getHeaders());
    randString = randomUnicodeString(100);
    assertEquals("/", options.getRequestURI());
    assertEquals(options, options.setRequestURI(randString));
    assertEquals(randString, options.getRequestURI());
    options.putHeader("foo", "bar");
    assertNotNull(options.getHeaders());
    assertEquals("bar", options.getHeaders().get("foo"));
    testComplete();
  }

  private void pausingServer(Consumer<HttpServer> consumer) {
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.pause();
      Handler<Message<Buffer>> resumeHandler = msg -> req.resume();
      vertx.eventBus().registerHandler("server_resume", resumeHandler);
      req.endHandler(v -> {
        vertx.eventBus().unregisterHandler("server_resume", resumeHandler);
      });

      req.dataHandler(buff -> {
        req.response().write(buff);
      });
    });

    server.listen(onSuccess(consumer));
  }

  private void drainingServer(Consumer<HttpServer> consumer) {
    server.requestHandler(req -> {
      req.response().setChunked(true);
      assertFalse(req.response().writeQueueFull());
      req.response().setWriteQueueMaxSize(1000);

      Buffer buff = randomBuffer(10000);
      //Send data until the buffer is full
      vertx.setPeriodic(1, id -> {
        req.response().write(buff);
        if (req.response().writeQueueFull()) {
          vertx.cancelTimer(id);
          req.response().drainHandler(v -> {
            assertFalse(req.response().writeQueueFull());
            testComplete();
          });

          // Tell the client to resume
          vertx.eventBus().send("client_resume", "");
        }
      });
    });

    server.listen(onSuccess(consumer));
  }

  private static MultiMap getHeaders(int num) {
    Map<String, String> map = genMap(num);
    MultiMap headers = new HttpHeadersAdapter(new DefaultHttpHeaders());
    for (Map.Entry<String, String> entry : map.entrySet()) {
      headers.add(entry.getKey(), entry.getValue());
    }
    return headers;
  }

  private static Map<String, String> genMap(int num) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < num; i++) {
      String key;
      do {
        key = randomAlphaString(1 + (int) ((19) * Math.random())).toLowerCase();
      } while (map.containsKey(key));
      map.put(key, randomAlphaString(1 + (int) ((19) * Math.random())));
    }
    return map;
  }

  private static String generateQueryString(Map<String, String> params, char delim) {
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (Map.Entry<String, String> param : params.entrySet()) {
      sb.append(param.getKey()).append("=").append(param.getValue());
      if (++count != params.size()) {
        sb.append(delim);
      }
    }
    return sb.toString();
  }

  private File setupFile(String fileName, String content) throws Exception {
    File file = new File(testDir, fileName);
    if (file.exists()) {
      file.delete();
    }
    file.deleteOnExit();
    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    out.write(content);
    out.close();
    return file;
  }
}
