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
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.HttpVersion;
import org.vertx.java.core.http.impl.HttpHeadersAdapter;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.vertx.java.tests.newtests.TestUtils.*;

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

    String pwd = randomUnicodeString(10);
    assertTrue(client.setKeyStorePassword(pwd) == client);
    assertTrue(client.getKeyStorePassword().equals(pwd));

    String path = randomUnicodeString(10);
    assertTrue(client.setKeyStorePath(path) == client);
    assertTrue(client.getKeyStorePath().equals(path));

    pwd = randomUnicodeString(10);
    assertTrue(client.setTrustStorePassword(pwd) == client);
    assertTrue(client.getTrustStorePassword().equals(pwd));

    path = randomUnicodeString(10);
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
    testURIAndPath("http://localhost:"+port+"/this/is/a/path/foo.html", "/this/is/a/path/foo.html");
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
    final Buffer body = randomBuffer(1000);
    server.requestHandler(req -> req.bodyHandler(buffer -> {
      assertEquals(body, buffer);
      req.response().end();
    }));

    server.listen(port, onSuccess(server -> {
      client.post("some-uri", resp -> testComplete()).end(body);
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
    final String body = randomUnicodeString(1000);
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
        req.response().end();
      });
    });

    server.listen(port, onSuccess(server -> {
      HttpClientRequest req = client.post("some-uri", resp -> testComplete());
      final int numWrites = 10;
      final int chunkSize = 100;

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

  private void testRequestBodyWriteString(final boolean chunked, final String encoding) {
    final String body = randomUnicodeString(1000);
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
  public void testRequestWriteBuffer() {
    final Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        assertEquals(body, buff);
        testComplete();
      });
    });

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.post("some-uri", noOpHandler());
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

  private void testStatusCode(final int code, final String statusMessage) {
    server.requestHandler(req -> {
      if (code != -1) {
        req.response().setStatusCode(code);
      }
      if (statusMessage != null) {
        req.response().setStatusMessage(statusMessage);
      }
      req.response().end();
    });

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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

  private void testResponseTrailers(final boolean individually) {
    final MultiMap trailers = getHeaders(10);

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

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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
        resp.exceptionHandler(new Handler<Throwable>() {
          public void handle(Throwable t) {
          }
        });
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

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", noOpHandler());
    }));

    await();
  }

  @Test
  public void testResponseBodyBufferAtEnd() {
    final Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().end(body);
    });

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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

  private void testResponseBodyStringAtEnd(final String encoding) {
    final String body = randomUnicodeString(1000);
    final Buffer bodyBuff;

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

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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

    server.listen(port, onSuccess(s -> {
      client.post("some-uri", noOpHandler()).end();
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

  private void testResponseBodyWriteBuffer(final boolean chunked) {
    final Buffer body = new Buffer();

    final int numWrites = 10;
    final int chunkSize = 100;

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

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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

  private void testResponseBodyWriteString(final boolean chunked, final String encoding) {
    final String body = randomUnicodeString(1000);
    final Buffer bodyBuff;

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

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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
    final Buffer body = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(port, onSuccess(s -> {
      client.post("some-uri", resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(body , buff);
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testPipelining() {
    final int requests = 100;

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

    server.listen(port, onSuccess(s -> {
      for (int count = 0; count < requests; count++) {
        final int theCount = count;
        HttpClientRequest req = client.post("some-uri", resp -> {
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

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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
    final String content = TestUtils.randomUnicodeString(10000);
    final File file = setupFile("test-send-file.html", content);

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(file.getAbsolutePath());
    });

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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
    final Buffer toSend = randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.put("someurl", resp -> {
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
    final Buffer toSend = randomBuffer(1000);
    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "100 Continue");
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.put("someurl", resp -> {
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
      HttpClientRequest req = client.get("someurl", noOpHandler());
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
      client.getNow("someurl", resp -> {
        resp.pause();
        final Handler<Message<Buffer>> resumeHandler = msg -> resp.resume();
        vertx.eventBus().registerHandler("client_resume", resumeHandler);
        resp.endHandler(v -> vertx.eventBus().unregisterHandler("client_resume", resumeHandler));
      });
    });

    await();
  }

  @Test
  public void testPooling() {
    testPooling(true);
  }

  @Test
  public void testPoolingNoKeepAlive() {
    testPooling(false);
  }

  private void testPooling(final boolean keepAlive) {
    final String path = "foo.txt";
    final int numGets = 1000;
    int maxPoolSize = 10;

    server.requestHandler(req -> {
      String cnt = req.headers().get("count");
      req.response().headers().set("count", cnt);
      req.response().end();
    });

    server.listen(port, onSuccess(s -> {
      client.setKeepAlive(keepAlive).setMaxPoolSize(maxPoolSize);

      final AtomicInteger cnt = new AtomicInteger(0);
      for (int i = 0; i < numGets; i++) {
        final int theCount = i;
        HttpClientRequest req = client.get(path, resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(theCount, Integer.parseInt(resp.headers().get("count")));
          if (cnt.incrementAndGet() == numGets) {
            testComplete();
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
    final AtomicInteger clientExceptions = new AtomicInteger();
    final AtomicInteger req2Exceptions = new AtomicInteger();
    final AtomicInteger req3Exceptions = new AtomicInteger();

    final Handler<String> checkEndHandler = name -> {
      if (clientExceptions.get() == 1 && req2Exceptions.get() == 1 && req3Exceptions.get() == 1) {
        testComplete();
      }
    };

    client.setPort(9998); // this simulates a connection error immediately
    client.exceptionHandler(t -> {
      assertEquals("More than more call to client exception handler was not expected", 1, clientExceptions.incrementAndGet());
      checkEndHandler.handle("Client");
    });

    // This one should cause an error in the Client Exception handler, because it has no exception handler set specifically.
    final HttpClientRequest req1 = client.get("someurl1", resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });
    // No exception handler set on request!

    final HttpClientRequest req2 = client.get("someurl2", resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });

    req2.exceptionHandler(t -> {
      assertEquals("More than more call to req2 exception handler was not expected", 1, req2Exceptions.incrementAndGet());
      checkEndHandler.handle("Request2");
    });

    final HttpClientRequest req3 = client.get("someurl2", resp -> {
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

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.get("timeoutTest", resp -> {
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

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.get("timeoutTest", resp -> {
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
    final AtomicReference<Throwable> exception = new AtomicReference<>();
    // There is no server running, should fail to connect
    final HttpClientRequest req = client.get("timeoutTest", resp -> {
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

    server.listen(port, onSuccess(s -> {
      final AtomicReference<Throwable> exception = new AtomicReference<>();

      // There is no server running, should fail to connect
      final HttpClientRequest req = client.get("timeoutTest", noOpHandler());
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

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.get("timeoutTest", resp -> fail("Response should not be handled"));
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
      client.setSSL(true);
      if (clientTrustAll) {
        client.setTrustAll(true);
      }
      if (clientTrust) {
        client.setTrustStorePath(findFileOnClasspath("tls/client-truststore.jks")).setTrustStorePassword("wibble");
      }
      if (clientCert) {
        client.setKeyStorePath(findFileOnClasspath("tls/client-keystore.jks")).setKeyStorePassword("wibble");
      }
      client.exceptionHandler(t -> {
        if (shouldPass) {
          fail("Should not throw exception");
        } else {
          testComplete();
        }
      });
      client.setPort(4043);
      HttpClientRequest req = client.get("someurl", response -> {
        response.bodyHandler(data -> assertEquals("bar", data.toString()));
        testComplete();
      });
      // NOTE: If you set a request handler now and an error happens on the request, the error is reported to the
      // request handler and NOT the client handler. Only if no handler is set on the request, or an error happens
      // that is not in the context of a request will the client handler get called. I can't figure out why an empty
      // handler was specified here originally, but if we want the client handler (specified above) to fire, we should
      // not set an empty handler here. The alternative would be to move the logic
//    req.exceptionHandler(new Handler<Throwable>() {
//      public void handle(Throwable t) {
//      }
//    });
      req.end("foo");
    });
    await();
  }

  @Test
  public void testConnectInvalidPort() {
    client.exceptionHandler(t -> testComplete());
    client.setPort(9998);
    client.getNow("someurl", resp -> fail("Connect should not be called"));

    await();
  }

  @Test
  public void testConnectInvalidHost() {
    client.setConnectTimeout(1000);
    client.exceptionHandler(t -> testComplete());
    client.setHost("127.0.0.2");
    client.getNow("someurl", resp -> fail("Connect should not be called"));

    await();
  }

  @Test
  public void testSetHandlersAfterListening() throws Exception {
    server.requestHandler(noOpHandler());

    server.listen(port, onSuccess(s -> {
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
      }).listen(port, onSuccess(s -> latchListen.countDown()));
    }
    assertTrue(latchListen.await(10, TimeUnit.SECONDS));

    // Create a bunch of connections
    CountDownLatch latchClient = new CountDownLatch(numRequests);
    client.setKeepAlive(false);
    for (int i = 0; i < numRequests; i++) {
      client.getNow("some-uri", res -> latchClient.countDown());
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
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer();
    theServer.requestHandler(req -> {
      fail("Should not process request");
    }).listen(port, onSuccess(s -> latch.countDown()));
    assertTrue(latch.await(10, TimeUnit.SECONDS));

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

    server.listen(port, onSuccess(s -> {
      client.head("some-uri", resp -> {
        assertEquals(41, Integer.parseInt(resp.headers().get("Content-Length")));
        resp.endHandler(v -> testComplete());
      }).end();
    }));

    await();
  }

  @Test
  public void testRemoteAddress() {
    server.requestHandler(req -> {
      assertEquals("localhost", req.remoteAddress().getHostName());
      req.response().end();
    });

    server.listen(port, "localhost", onSuccess(s -> {
      client.getNow("some-uri", resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testGetAbsoluteURI() {
    server.requestHandler(req -> {
      assertEquals("http://localhost:" + port + "/foo/bar", req.absoluteURI().toString());
      req.response().end();
    });

    server.listen(port, "localhost", onSuccess(s -> {
      client.getNow("/foo/bar", resp -> resp.endHandler(v -> testComplete()));
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

  @Test
  public void testListenInvalidHost() {
    server.requestHandler(noOpHandler());
    server.listen(port, "iqwjdoqiwjdoiqwdiojwd", onFailure(s -> testComplete()));
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
    final HttpClientRequest clientRequest = client.get("some-uri", resp -> {
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

    server.listen(port, onSuccess(s -> clientRequest.end()));

    await();
  }

  @Test
  public void testHttpVersion() {
    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_1_1, req.version());
      req.response().end();
    });

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> resp.endHandler(v -> testComplete()));
    }));

    await();
  }

  @Test
  public void testFormUploadFile() throws Exception {
    final AtomicInteger attributeCount = new AtomicInteger();
    final String content = "Vert.x rocks!";

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

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.post("/form", resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(0, attributeCount.get());
        testComplete();
      });

      final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
      Buffer buffer = new Buffer();
      final String body =
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
    final AtomicInteger attributeCount = new AtomicInteger();
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

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.post("/form", resp -> {
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
    final AtomicInteger attributeCount = new AtomicInteger();
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

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.post("/form", resp -> {
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
    final Buffer toSend = randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "101 Upgrade");
      req.bodyHandler(data -> {
        assertTrue(buffersEqual(toSend, data));
        req.response().end();
      });
    });

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.get("someurl", resp -> {
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

    server.listen(port, onSuccess(s -> {
      HttpClientRequest req = client.get("some-uri", resp -> testComplete());
      req.putHeader("Host", "localhost:4444");
      req.end();
    }));

    await();
  }

  @Test
  public void testSetGetMaxWebSocketFrameSizeServer() {
    int size = 61231763;
    assertTrue(server == server.setMaxWebSocketFrameSize(size));
    assertTrue(size == server.getMaxWebSocketFrameSize());
    testComplete();
  }

  @Test
  public void testSetGetMaxWebSocketFrameSizeClient() {
    int size = 61231763;
    assertTrue(client == client.setMaxWebSocketFrameSize(size));
    assertTrue(size == client.getMaxWebSocketFrameSize());
    testComplete();
  }

  @Test
  public void testResponseBodyWriteFixedString() {
    final String body = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
    final Buffer bodyBuff = new Buffer(body);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(port, onSuccess(s -> {
      client.get("some-uri", resp -> {
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
    final Buffer buffer = randomBuffer(128);
    final Buffer received = new Buffer();
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
      server.listen(port, onSuccess(s -> {
        client.connect("some-uri", resp -> {
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

    client.setKeepAlive(false);
    client.setMaxPoolSize(1);
    server.listen(port, onSuccess(s -> {
      // Add a few requests that should all timeout
      for (int i = 0; i < 5; i++) {
        HttpClientRequest req = client.get("some-uri", resp -> {
          fail("Should not be called");
        });
        req.exceptionHandler(t -> assertTrue(t instanceof TimeoutException));
        req.setTimeout(500);
        req.end();
      }
      // Now another request that should not timeout
      HttpClientRequest req = client.get("some-uri", resp -> {
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
    File file = new File(VERTX_FILE_BASE, "testdirectory");
    server.requestHandler(req -> {
      vertx.fileSystem().mkdir(file.getAbsolutePath(), onSuccess(v -> {
        req.response().sendFile(file.getAbsolutePath());
      }));
    });

    server.listen(port, onSuccess(s -> {
      client.getNow("some-uri", resp -> {
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

    server.listen(port, onSuccess(consumer));
  }

  private void drainingServer(Consumer<HttpServer> consumer) {
    server.requestHandler(req -> {
      req.response().setChunked(true);
      assertFalse(req.response().writeQueueFull());
      req.response().setWriteQueueMaxSize(1000);

      final Buffer buff = randomBuffer(10000);
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

    server.listen(port, onSuccess(consumer));
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
