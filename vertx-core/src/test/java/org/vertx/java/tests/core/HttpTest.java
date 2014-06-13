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
  }

  @Test
  public void testClientOptions() {
    ClientOptions options = new ClientOptions();

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

  }


  @Test
  public void testServerDefaults() {
    assertFalse(server.isSSL());
    assertNull(server.getKeyStorePassword());
    assertNull(server.getKeyStorePath());
    assertNull(server.getTrustStorePassword());
    assertNull(server.getTrustStorePath());
    assertTrue(server.isReuseAddress());
  }

  @Test
  public void testServerAttributes() {
    assertTrue(server.setSSL(false) == server);
    assertFalse(server.isSSL());
    assertTrue(server.setSSL(true) == server);
    assertTrue(server.isSSL());

    String pwd = randomUnicodeString(10);
    assertTrue(server.setKeyStorePassword(pwd) == server);
    assertEquals(pwd, server.getKeyStorePassword());

    String path = randomUnicodeString(10);
    assertTrue(server.setKeyStorePath(path) == server);
    assertEquals(path, server.getKeyStorePath());

    pwd = randomUnicodeString(10);
    assertTrue(server.setTrustStorePassword(pwd) == server);
    assertEquals(pwd, server.getTrustStorePassword());

    path = randomUnicodeString(10);
    assertTrue(server.setTrustStorePath(path) == server);
    assertEquals(path, server.getTrustStorePath());

    assertTrue(server.setReuseAddress(true) == server);
    assertTrue(server.isReuseAddress());
    assertTrue(server.setReuseAddress(false) == server);
    assertFalse(server.isReuseAddress());

    assertTrue(server.setSoLinger(10) == server);
    assertEquals(10, server.getSoLinger());

    assertTrue(server.setTCPKeepAlive(true) == server);
    assertTrue(server.isTCPKeepAlive());
    assertTrue(server.setTCPKeepAlive(false) == server);
    assertFalse(server.isTCPKeepAlive());

    assertTrue(server.setTCPNoDelay(true) == server);
    assertTrue(server.isTCPNoDelay());
    assertTrue(server.setTCPNoDelay(false) == server);
    assertFalse(server.isTCPNoDelay());

    int rbs = new Random().nextInt(1024 * 1024) + 1;
    assertTrue(server.setReceiveBufferSize(rbs) == server);
    assertEquals(rbs, server.getReceiveBufferSize());

    try {
      server.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setReceiveBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    assertTrue(server.setSendBufferSize(sbs) == server);
    assertEquals(sbs, server.getSendBufferSize());

    try {
      server.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setSendBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    assertTrue(server.setTrafficClass(trafficClass) == server);
    assertEquals(trafficClass, server.getTrafficClass());
  }

  @Test
  public void testServerChaining() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertTrue(req.response().setChunked(true) == req.response());
      assertTrue(req.response().write("foo", "UTF-8") == req.response());
      assertTrue(req.response().write("foo") == req.response());
      testComplete();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testServerChainingSendFile() throws Exception {
    client = vertx.createHttpClient(new ClientOptions());
    File file = setupFile("test-server-chaining.dat", "blah");
    server.requestHandler(req -> {
      assertTrue(req.response().sendFile(file.getAbsolutePath()) == req.response());
      file.delete();
      testComplete();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testClientChaining() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(noOpHandler());

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      HttpClientRequest req = client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
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
  public void testSimpleGET() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "GET", client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimplePUT() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PUT", client.put(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimplePOST() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "POST", client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleDELETE() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "DELETE", client.delete(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleHEAD() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "HEAD", client.head(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleTRACE() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "TRACE", client.trace(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleCONNECT() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "CONNECT", client.connect(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleOPTIONS() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "OPTIONS", client.options(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimplePATCH() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PATCH", client.patch(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete()));
  }

  @Test
  public void testSimpleGETNonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "GET", resp -> testComplete());
  }

  @Test
  public void testSimplePUTNonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PUT", resp -> testComplete());
  }

  @Test
  public void testSimplePOSTNonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "POST", resp -> testComplete());
  }

  @Test
  public void testSimpleDELETENonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "DELETE", resp -> testComplete());
  }

  @Test
  public void testSimpleHEADNonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "HEAD", resp -> testComplete());
  }

  @Test
  public void testSimpleTRACENonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "TRACE", resp -> testComplete());
  }

  @Test
  public void testSimpleCONNECTNonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "CONNECT", resp -> testComplete());
  }

  @Test
  public void testSimpleOPTIONSNonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "OPTIONS", resp -> testComplete());
  }

  @Test
  public void testSimplePATCHNonSpecific() {
    client = vertx.createHttpClient(new ClientOptions());
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PATCH", resp -> testComplete());
  }

  private void testSimpleRequest(String uri, String method, Handler<HttpClientResponse> handler) {
    testSimpleRequest(uri, method, client.request(method, new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), handler));
  }

  private void testSimpleRequest(String uri, String method, HttpClientRequest request) {
    String path = uri.indexOf('?') == -1 ? uri : uri.substring(0, uri.indexOf('?'));
    server.requestHandler(req -> {
      assertEquals(path, req.path());
      assertEquals(method, req.method());
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> request.end()));

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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertEquals(uri, req.uri());
      assertEquals(path, req.path());
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(uri), resp -> testComplete());
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI("some-uri/?" + query), resp -> testComplete());
    }));

    await();
  }

  @Test
  public void testNoParams() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertNull(req.query());
      assertTrue(req.params().isEmpty());
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
    }));

    await();
  }

  @Test
  public void testDefaultRequestHeaders() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertEquals(1, req.headers().size());
      assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
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
    client = vertx.createHttpClient(new ClientOptions());
    MultiMap headers = getHeaders(10);

    server.requestHandler(req -> {
      assertEquals(headers.size() + 1, req.headers().size());
      for (Map.Entry<String, String> entry : headers) {
        assertEquals(entry.getValue(), req.headers().get(entry.getKey()));
      }
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(noOpHandler());

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      HttpClientRequest req = client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer body = randomBuffer(1000);
    server.requestHandler(req -> req.bodyHandler(buffer -> {
      assertEquals(body, buffer);
      req.response().end();
    }));

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
      client.post(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete()).end(body);
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer body = new Buffer();

    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(body, buffer);
        req.response().end();
      });
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(server -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        assertEquals(body, buff);
        testComplete();
      });
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      if (code != -1) {
        req.response().setStatusCode(code);
      }
      if (statusMessage != null) {
        req.response().setStatusMessage(statusMessage);
      }
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), noOpHandler());
    }));

    await();
  }

  @Test
  public void testResponseBodyBufferAtEnd() {
    client = vertx.createHttpClient(new ClientOptions());
    Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().end(body);
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      try {
        req.response().write("foo");
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        //OK
        testComplete();
      }
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
  public void testPipelining() {
    // FIXME - is this really pipeliing?
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile("test-send-file.html", content);

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(file.getAbsolutePath());
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer toSend = randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer toSend = randomBuffer(1000);
    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "100 Continue");
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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
    client = vertx.createHttpClient(new ClientOptions());
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

    client = vertx.createHttpClient(new ClientOptions().setKeepAlive(keepAlive).setPipelining(pipelining).setMaxPoolSize(maxPoolSize));

    server.requestHandler(req -> {
      String cnt = req.headers().get("count");
      req.response().headers().set("count", cnt);
      req.response().end();
    });

    AtomicBoolean completeAlready = new AtomicBoolean();

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {

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
    client = vertx.createHttpClient(new ClientOptions());

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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(noOpHandler()); // No response handler so timeout triggers

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> req.response().end());

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      vertx.setTimer(500, id -> {
        req.response().setStatusCode(200);
        req.response().end("OK");
      });
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    ClientOptions options = new ClientOptions();
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
    server.setSSL(true);
    if (serverTrust) {
      server.setTrustStorePath(findFileOnClasspath("tls/server-truststore.jks")).setTrustStorePassword("wibble");
    }
    if (serverCert) {
      server.setKeyStorePath(findFileOnClasspath("tls/server-keystore.jks")).setKeyStorePassword("wibble");
    }
    if (requireClientAuth) {
      server.setClientAuthRequired(true);
    }
    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals("foo", buffer.toString());
        req.response().end("bar");
      });
    });
    server.listen(4043, ar -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    client.exceptionHandler(t -> testComplete());
    client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setPort(9998).setRequestURI(DEFAULT_TEST_URI), resp -> fail("Connect should not be called"));

    await();
  }

  @Test
  public void testConnectInvalidHost() {
    client = vertx.createHttpClient(new ClientOptions().setConnectTimeout(1000));
    client.exceptionHandler(t -> testComplete());
    client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setHost("127.0.0.2").setRequestURI(DEFAULT_TEST_URI), resp -> fail("Connect should not be called"));

    await();
  }

  @Test
  public void testSetHandlersAfterListening() throws Exception {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(noOpHandler());

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions().setKeepAlive(false));
    int numServers = 5;
    int numRequests = numServers * 100;

    List<HttpServer> servers = new ArrayList<>();
    Set<HttpServer> connectedServers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Map<HttpServer, Integer> requestCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numRequests);
    for (int i = 0; i < numServers; i++) {
      HttpServer theServer = vertx.createHttpServer();
      servers.add(theServer);
      theServer.requestHandler(req -> {
        connectedServers.add(theServer);
        Integer cnt = requestCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        requestCount.put(theServer, icnt);
        latchConns.countDown();
        req.response().end();
      }).listen(DEFAULT_HTTP_PORT, onSuccess(s -> latchListen.countDown()));
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
    HttpServer theServer = vertx.createHttpServer();
    theServer.requestHandler(req -> {
      fail("Should not process request");
    }).listen(8081, onSuccess(s -> latch.countDown()));
    awaitLatch(latch);

    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer();
    theServer.requestHandler(req -> {
      fail("Should not process request");
    }).listen(DEFAULT_HTTP_PORT, onSuccess(s -> latch.countDown()));
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertEquals("HEAD", req.method());
      // Head never contains a body but it can contain a Content-Length header
      // Since headers from HEAD must correspond EXACTLY with corresponding headers for GET
      req.response().headers().set("Content-Length", String.valueOf(41));
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      client.head(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(41, Integer.parseInt(resp.headers().get("Content-Length")));
        resp.endHandler(v -> testComplete());
      }).end();
    }));

    await();
  }

  @Test
  public void testRemoteAddress() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertEquals(DEFAULT_HTTP_HOST, req.remoteAddress().getHostName());
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testGetAbsoluteURI() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertEquals("http://localhost:" + DEFAULT_HTTP_PORT + "/foo/bar", req.absoluteURI().toString());
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI("/foo/bar"), resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testListenInvalidPort() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(noOpHandler()).listen(1128371831, onFailure(server -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testListenInvalidHost() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(noOpHandler());
    server.listen(DEFAULT_HTTP_PORT, "iqwjdoqiwjdoiqwdiojwd", onFailure(s -> testComplete()));
  }

  @Test
  public void testPauseClientResponse() {
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> clientRequest.end()));

    await();
  }

  @Test
  public void testHttpVersion() {
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testFormUploadFile() throws Exception {
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      client = vertx.createHttpClient(new ClientOptions());
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer toSend = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "101 Upgrade");
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    server.requestHandler(req -> {
      assertEquals("localhost:4444", req.headers().get("Host"));
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      HttpClientRequest req = client.get(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> testComplete());
      req.putHeader("Host", "localhost:4444");
      req.end();
    }));

    await();
  }

  @Test
  public void testSetGetMaxWebSocketFrameSizeServer() {
    client = vertx.createHttpClient(new ClientOptions());
    int size = 61231763;
    assertTrue(server == server.setMaxWebSocketFrameSize(size));
    assertTrue(size == server.getMaxWebSocketFrameSize());
    testComplete();
  }

  @Test
  public void testResponseBodyWriteFixedString() {
    client = vertx.createHttpClient(new ClientOptions());
    String body = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
    Buffer bodyBuff = new Buffer(body);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    Buffer buffer = randomBuffer(128);
    Buffer received = new Buffer();
    vertx.createNetServer().connectHandler(socket -> {
      socket.dataHandler(socket::write);
    }).listen(1235, onSuccess(netServer -> {
      server.requestHandler(req -> {
        vertx.createNetClient().connect(netServer.port(), onSuccess(socket -> {
          req.response().setStatusCode(200);
          req.response().setStatusMessage("Connection established");
          req.response().end();

          // Create pumps which echo stuff
          Pump.createPump(req.netSocket(), socket).start();
          Pump.createPump(socket, req.netSocket()).start();
          req.netSocket().closeHandler(v -> socket.close());
        }));
      });
      server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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

    client = vertx.createHttpClient(new ClientOptions().setKeepAlive(false).setMaxPoolSize(1));

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
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
    client = vertx.createHttpClient(new ClientOptions());
    File file = new File(testDir, "testdirectory");
    server.requestHandler(req -> {
      vertx.fileSystem().mkdir(file.getAbsolutePath(), onSuccess(v -> {
        req.response().sendFile(file.getAbsolutePath());
      }));
    });

    server.listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      client.getNow(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setRequestURI(DEFAULT_TEST_URI), resp -> {
        assertEquals(403, resp.statusCode());
        vertx.fileSystem().delete(file.getAbsolutePath(), v -> testComplete());
      });
    }));

    await();
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(consumer));
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

    server.listen(DEFAULT_HTTP_PORT, onSuccess(consumer));
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
