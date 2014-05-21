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

package org.vertx.java.tests.newtests;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.impl.HttpHeadersAdapter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpTest extends VertxTestBase {

  private static final int DEFAULT_HTTP_PORT = Integer.getInteger("vertx.http.port", 8080);

  public static final File VERTX_FILE_BASE;
  static {
    try {
      final File vertxFileBase = Files.createTempDirectory("vertx-test").toFile();
      vertxFileBase.deleteOnExit();
      VERTX_FILE_BASE = vertxFileBase;
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private HttpServer server;
  private HttpClient client;
  private int port = DEFAULT_HTTP_PORT;

  @Before
  public void before() throws Exception {
    super.before();
    server = vertx.createHttpServer();
    client = vertx.createHttpClient().setPort(port);
  }

  @After
  public void after() throws Exception {
    client.close();
    CountDownLatch latch = new CountDownLatch(1);
    server.close((asyncResult) -> {
      latch.countDown();
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    super.after();
  }

  @Test
  public void testClientDefaults() {
    assertFalse(client.isSSL());
    assertTrue(client.isVerifyHost());
    assertNull(client.getKeyStorePassword());
    assertNull(client.getKeyStorePath());
    assertNull(client.getTrustStorePassword());
    assertNull(client.getTrustStorePath());
    testComplete();
  }

  @Test
  public void testClientAttributes() {
    assertTrue(client.setSSL(false) == client);
    assertFalse(client.isSSL());
    assertTrue(client.setSSL(true) == client);
    assertTrue(client.isSSL());

    assertTrue(client.setVerifyHost(false) == client);
    assertFalse(client.isVerifyHost());
    assertTrue(client.setVerifyHost(true) == client);
    assertTrue(client.isVerifyHost());

    String pwd = TestUtils.randomUnicodeString(10);
    assertTrue(client.setKeyStorePassword(pwd) == client);
    assertTrue(client.getKeyStorePassword().equals(pwd));

    String path = TestUtils.randomUnicodeString(10);
    assertTrue(client.setKeyStorePath(path) == client);
    assertTrue(client.getKeyStorePath().equals(path));

    pwd = TestUtils.randomUnicodeString(10);
    assertTrue(client.setTrustStorePassword(pwd) == client);
    assertTrue(client.getTrustStorePassword().equals(pwd));

    path = TestUtils.randomUnicodeString(10);
    assertTrue(client.setTrustStorePath(path) == client);
    assertTrue(client.getTrustStorePath().equals(path));

    assertTrue(client.setReuseAddress(true) == client);
    assertTrue(client.isReuseAddress());
    assertTrue(client.setReuseAddress(false) == client);
    assertFalse(client.isReuseAddress());

    assertTrue(client.setSoLinger(10) == client);
    assertEquals(10, client.getSoLinger());

    assertTrue(client.setTCPKeepAlive(true) == client);
    assertTrue(client.isTCPKeepAlive());
    assertTrue(client.setTCPKeepAlive(false) == client);
    assertFalse(client.isTCPKeepAlive());

    assertTrue(client.setTCPNoDelay(true) == client);
    assertTrue(client.isTCPNoDelay());
    assertTrue(client.setTCPNoDelay(false) == client);
    assertFalse(client.isTCPNoDelay());

    int rbs = new Random().nextInt(1024 * 1024) + 1;
    assertTrue(client.setReceiveBufferSize(rbs) == client);
    assertEquals(rbs, client.getReceiveBufferSize());

    try {
      client.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setReceiveBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    assertTrue(client.setSendBufferSize(sbs) == client);
    assertEquals(sbs, client.getSendBufferSize());

    try {
      client.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setSendBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    assertTrue(client.setTrafficClass(trafficClass) == client);
    assertEquals(trafficClass, client.getTrafficClass());

    testComplete();
  }

  @Test
  public void testServerDefaults() {
    assertFalse(server.isSSL());
    assertNull(server.getKeyStorePassword());
    assertNull(server.getKeyStorePath());
    assertNull(server.getTrustStorePassword());
    assertNull(server.getTrustStorePath());
    assertTrue(server.isReuseAddress());
    testComplete();
  }

  @Test
  public void testServerAttributes() {
    assertTrue(server.setSSL(false) == server);
    assertFalse(server.isSSL());
    assertTrue(server.setSSL(true) == server);
    assertTrue(server.isSSL());


    String pwd = TestUtils.randomUnicodeString(10);
    assertTrue(server.setKeyStorePassword(pwd) == server);
    assertEquals(pwd, server.getKeyStorePassword());

    String path = TestUtils.randomUnicodeString(10);
    assertTrue(server.setKeyStorePath(path) == server);
    assertEquals(path, server.getKeyStorePath());

    pwd = TestUtils.randomUnicodeString(10);
    assertTrue(server.setTrustStorePassword(pwd) == server);
    assertEquals(pwd, server.getTrustStorePassword());

    path = TestUtils.randomUnicodeString(10);
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

    server.listen(port, onSuccess(server -> {
      client.put("someurl", noOpHandler()).end();
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

    server.listen(port, onSuccess(server -> {
      client.put("someurl", noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testClientChaining() {
    server.requestHandler(noOpHandler());

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.put("someurl", noOpHandler());
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

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.get("some-uri", resp -> {
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
  public void testNoContext() {
    //TODO: I don't think we need this one anymore
    testComplete();
  }

  @Test
  public void testSimpleGET() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "GET", client.get(uri, resp -> testComplete()));
  }

  @Test
  public void testSimplePUT() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PUT", client.put(uri, resp -> testComplete()));
  }

  @Test
  public void testSimplePOST() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "POST", client.post(uri, resp -> testComplete()));
  }

  @Test
  public void testSimpleDELETE() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "DELETE", client.delete(uri, resp -> testComplete()));
  }

  @Test
  public void testSimpleHEAD() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "HEAD", client.head(uri, resp -> testComplete()));
  }

  @Test
  public void testSimpleTRACE() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "TRACE", client.trace(uri, resp -> testComplete()));
  }

  @Test
  public void testSimpleCONNECT() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "CONNECT", client.connect(uri, resp -> testComplete()));
  }

  @Test
  public void testSimpleOPTIONS() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "OPTIONS", client.options(uri, resp -> testComplete()));
  }

  @Test
  public void testSimplePATCH() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, "PATCH", client.patch(uri, resp -> testComplete()));
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
    testSimpleRequest(uri, method, client.request(method, uri, handler));
  }

  private void testSimpleRequest(String uri, String method, HttpClientRequest request) {
    String path = uri.indexOf('?') == -1 ? uri : uri.substring(0, uri.indexOf('?'));
    server.requestHandler(req -> {
      assertEquals(path, req.path());
      assertEquals(method, req.method());
      req.response().end();
    });

    server.listen(port, onSuccess(server -> request.end()));

    await();
  }

  @Test
  public void testAbsoluteURI() {
    testURIAndPath("http://localhost:8080/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
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

    server.listen(port, onSuccess(server -> {
      client.getNow(uri, resp -> testComplete());
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
    final Map<String, String> params = genMap(10);
    final String query = generateQueryString(params, delim);

    server.requestHandler(req -> {
      assertEquals(query, req.query());
      assertEquals(params.size(), req.params().size());
      for (Map.Entry<String, String> entry : req.params()) {
        assertEquals(entry.getValue(), params.get(entry.getKey()));
      }
      req.response().end();
    });

    server.listen(port, onSuccess(server -> {
      client.getNow("some-uri/?" + query, resp -> testComplete());
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

    server.listen(port, onSuccess(server -> {
      client.getNow("some-uri", resp -> testComplete());
    }));

    await();
  }

  @Test
  public void testDefaultRequestHeaders() {
    server.requestHandler(req -> {
      assertEquals(1, req.headers().size());
      assertEquals("localhost:" + port, req.headers().get("host"));
      req.response().end();
    });

    server.listen(port, onSuccess(server -> {
      client.getNow("some-uri", resp -> testComplete());
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

  private void testRequestHeaders(final boolean individually) {
    final MultiMap headers = getHeaders(10);

    server.requestHandler(req -> {
      assertEquals(headers.size() + 1, req.headers().size());
      for (Map.Entry<String, String> entry : headers) {
        assertEquals(entry.getValue(), req.headers().get(entry.getKey()));
      }
      req.response().end();
    });

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.get("some-uri", resp -> testComplete());
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

  private void testResponseHeaders(final boolean individually) {
    final MultiMap headers = getHeaders(10);

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

    server.listen(port, onSuccess(server -> {
      client.getNow("some-uri", resp -> {
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

  private void testResponseMultipleSetCookie(final boolean inHeader, final boolean inTrailer) {
    final List<String> cookies = new ArrayList<>();

    server.requestHandler(req -> {
      if (inHeader) {
        final List<String> headers = new ArrayList<>();
        headers.add("h1=h1v1");
        headers.add("h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
        cookies.addAll(headers);
        req.response().headers().set("Set-Cookie", headers);
      }
      if (inTrailer) {
        req.response().setChunked(true);
        final List<String> trailers = new ArrayList<>();
        trailers.add("t1=t1v1");
        trailers.add("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
        cookies.addAll(trailers);
        req.response().trailers().set("Set-Cookie", trailers);
      }
      req.response().end();
    });

    server.listen(port, onSuccess(server -> {
      client.getNow("some-uri", resp -> {
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

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.post("some-uri", noOpHandler());
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
    final Buffer body = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> req.bodyHandler(buffer -> {
      assertEquals(body, buffer);
      testComplete();
    }));

    server.listen(port, onSuccess(server -> {
      client.post("some-uri", resp -> noOpHandler()).end(body);
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

  private void testRequestBodyStringAtEnd(final String encoding) {
    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

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

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.post("some-uri", noOpHandler());
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

  private void testRequestBodyWriteBuffer(final boolean chunked) {
    final Buffer body = new Buffer();

    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(body, buffer);
        testComplete();
      });
    });

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.post("some-uri", noOpHandler());
      final int numWrites = 10;
      final int chunkSize = 100;

      if (chunked) {
        req.setChunked(true);
      } else {
        req.headers().set("Content-Length", String.valueOf(numWrites * chunkSize));
      }
      for (int i = 0; i < numWrites; i++) {
        Buffer b = TestUtils.randomBuffer(chunkSize);
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

  private void testRequestBodyWriteString(final boolean chunked, final String encoding) {
    final String body = TestUtils.randomUnicodeString(1000);
    final Buffer bodyBuff;

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

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.post("some-uri", noOpHandler());

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
  public void testListenInvalidPort() {
    server.requestHandler(noOpHandler()).listen(1128371831, onFailure(server -> {
      testComplete();
    }));
    await();
  }

  private <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
    return result -> {
      assertTrue(result.succeeded());
      consumer.accept(result.result());
    };
  }

  private <T> Handler<AsyncResult<T>> onFailure(Consumer<T> consumer) {
    return result -> {
      assertFalse(result.succeeded());
      consumer.accept(result.result());
    };
  }

  @SuppressWarnings("unchecked")
  public <E> Handler<E> noOpHandler() {
    return noOp;
  }

  private static final Handler noOp = e -> {
  };

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
        key = TestUtils.randomAlphaString(1 + (int) ((19) * Math.random())).toLowerCase();
      } while (map.containsKey(key));
      map.put(key, TestUtils.randomAlphaString(1 + (int) ((19) * Math.random())));
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

  private static File setupFile(String fileName, String content) throws Exception {
    File file = new File(VERTX_FILE_BASE, fileName);
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
