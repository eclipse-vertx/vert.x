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

package io.vertx.tests.http;

import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Exception;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.CleanableHttpClient;
import io.vertx.core.http.impl.HttpClientImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.http.impl.ServerCookie;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.HAProxyMessageCompletionHandler;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.DetectFileDescriptorLeaks;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakedns.FakeDNSServer;
import io.vertx.test.fakestream.FakeStream;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.netty.TestLoggerFactory;
import io.vertx.test.proxy.HAProxy;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.net.ServerSocket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.IntStream;

import static io.vertx.core.http.HttpMethod.*;
import static io.vertx.test.core.AssertExpectations.that;
import static io.vertx.test.core.TestUtils.*;
import static org.hamcrest.CoreMatchers.instanceOf;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpTest extends HttpTestBase {

  protected abstract HttpVersion clientAlpnProtocolVersion();
  protected abstract HttpVersion serverAlpnProtocolVersion();

  @Test
  public void testCloseMulti() throws Exception {
    int num = 4;
    waitFor(num);
    HttpServer[] servers = new HttpServer[num];
    for (int i = 0;i < num;i++) {
      int val = i;
      servers[i] = vertx.createHttpServer(createBaseServerOptions()).requestHandler(req -> {
        req.response().end("Server " + val);
      });
      startServer(testAddress, servers[i]);
    }
    List<HttpClientConnection> connections = Collections.synchronizedList(new ArrayList<>());
    for (int i = 0;i < num;i++) {
      client.connect(new HttpConnectOptions().setServer(testAddress))
        .onComplete(onSuccess(connections::add));
    }
    waitUntil(() -> connections.size() == 4);
    Set<String> actual = new HashSet<>();
    Set<String> expected = new HashSet<>();
    for (int i = 0;i < num;i++) {
      Buffer body = awaitFuture(connections.get(i)
        .request(requestOptions)
        .compose(req -> req.send()
          .compose(HttpClientResponse::body)
        ));
      expected.add("Server " + i);
      actual.add(body.toString());
    }
    assertEquals(expected, actual);
    awaitFuture(servers[3].close());
    int successes = 0;
    int failures = 0;
    for (int i = 0;i < num;i++) {
      Future<Buffer> fut = connections.get(i)
        .request(requestOptions)
        .compose(req -> req.send()
          .compose(HttpClientResponse::body)
        );
      try {
        awaitFuture(fut);
        successes++;
      } catch (Exception e) {
        failures++;
      }
    }
    assertEquals(3, successes);
    assertEquals(1, failures);
  }

  @Rule
  public TemporaryFolder testFolder = TemporaryFolder.builder().assureDeletion().build();

  protected File testDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testDir = testFolder.newFolder();
  }

  @Test
  public void testClientRequestArguments() throws Exception {
    server.requestHandler(req -> {
     fail();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      assertNullPointerException(() -> req.putHeader((String) null, "someValue"));
      assertNullPointerException(() -> req.putHeader((CharSequence) null, "someValue"));
      assertNullPointerException(() -> req.putHeader("someKey", (Iterable<String>) null));
      assertNullPointerException(() -> req.write((Buffer) null));
      assertNullPointerException(() -> req.write((String) null));
      assertNullPointerException(() -> req.write(null, "UTF-8"));
      assertNullPointerException(() -> req.write("someString", (String) null));
      assertNullPointerException(() -> req.end((Buffer) null));
      assertNullPointerException(() -> req.end((String) null));
      assertNullPointerException(() -> req.end(null, "UTF-8"));
      assertNullPointerException(() -> req.end("someString", (String) null));
      assertIllegalArgumentException(() -> req.idleTimeout(0));
      testComplete();
    }));
    await();
  }

  @Test
  public void testListenSocketAddress() throws Exception {
    NetClient netClient = vertx.createNetClient();
    server.close();
    server = vertx.createHttpServer().requestHandler(req -> req.response().end());
    SocketAddress sockAddress = SocketAddress.inetSocketAddress(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
    startServer(sockAddress);
    netClient
      .connect(sockAddress)
      .onComplete(onSuccess(sock -> {
        sock.handler(buf -> {
          assertTrue("Response is not an http 200", buf.toString("UTF-8").startsWith("HTTP/1.1 200 OK"));
          testComplete();
        });
        sock.write("GET / HTTP/1.1\r\n\r\n");
      }));

    try {
      await();
    } finally {
      netClient.close();
    }
  }

  @Test
  public void testListenDomainSocketAddress() throws Exception {
    Vertx vx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    Assume.assumeTrue("Native transport must be enabled", vx.isNativeTransportEnabled());
    Assume.assumeTrue("Transport must support domain sockets", ((VertxInternal) vx).transport().supportsDomainSockets());
    int len = 3;
    waitFor(len * len);
    List<SocketAddress> addresses = new ArrayList<>();
    for (int i = 0;i < len;i++) {
      File sockFile = TestUtils.tmpFile(".sock");
      SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
      HttpServer server = vx
        .createHttpServer(createBaseServerOptions())
        .requestHandler(req -> req.response().end(sockAddress.path()));
      startServer(sockAddress, server);
      addresses.add(sockAddress);
    }
    HttpClient client = vx.createHttpClient(createBaseClientOptions());
    for (int i = 0;i < len;i++) {
      SocketAddress sockAddress = addresses.get(i);
      for (int j = 0;j < len;j++) {
        client
          .request(new RequestOptions(requestOptions).setServer(sockAddress))
          .compose(req -> req
            .send()
            .compose(resp -> {
              assertEquals(200, resp.statusCode());
              return resp.body();
            }))
          .onComplete(onSuccess(body -> {
            assertEquals(sockAddress.path(), body.toString());
            complete();
          }));
      }
    }
    try {
      await();
    } finally {
      vx.close();
    }
  }


  @Test
  public void testLowerCaseHeaders() throws Exception {
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

    startServer(testAddress);
    client
      .request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.putHeader("Foo", "foo");
        assertEquals("foo", req.headers().get("Foo"));
        assertEquals("foo", req.headers().get("foo"));
        assertEquals("foo", req.headers().get("fOO"));
        assertTrue(req.headers().contains("Foo"));
        assertTrue(req.headers().contains("foo"));
        assertTrue(req.headers().contains("fOO"));
        req
          .send()
          .onComplete(onSuccess(resp -> {
            assertEquals("quux", resp.headers().get("Quux"));
            assertEquals("quux", resp.headers().get("quux"));
            assertEquals("quux", resp.headers().get("qUUX"));
            assertTrue(resp.headers().contains("Quux"));
            assertTrue(resp.headers().contains("quux"));
            assertTrue(resp.headers().contains("qUUX"));
            testComplete();
          }));
      }));

    await();
  }

  @Test
  public void testServerActualPortWhenSet() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    server.close();
    server
        .requestHandler(request -> {
          request.response().end("hello");
        });
    startServer();
    assertEquals(server.actualPort(), DEFAULT_HTTP_PORT);
    HttpClient client = vertx.createHttpClient(createBaseClientOptions());
    client
      .request(new RequestOptions(requestOptions).setPort(server.actualPort()))
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(resp.statusCode(), 200);
          return resp.body();
        }))
      .onComplete(onSuccess(body -> {
        assertEquals(body.toString("UTF-8"), "hello");
        testComplete();
      }));
    await();
  }

  @Test
  public void testServerActualPortWhenZero() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setPort(0).setHost(DEFAULT_HTTP_HOST));
    server
        .requestHandler(request -> {
          request.response().end("hello");
        });
    startServer();
    assertTrue(server.actualPort() != 0);
    HttpClient client = vertx.createHttpClient(createBaseClientOptions());
    client
      .request(new RequestOptions(requestOptions).setPort(server.actualPort()))
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(resp.statusCode(), 200);
          return resp.body();
        }))
      .onComplete(onSuccess(body -> {
        assertEquals(body.toString("UTF-8"), "hello");
        testComplete();
      }));
    await();
  }

  @Test
  public void testServerActualPortWhenZeroPassedInListen() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions(createBaseServerOptions()).setHost(DEFAULT_HTTP_HOST));
    server
        .requestHandler(request -> {
          request.response().end("hello");
        });
    startServer();
    assertTrue(server.actualPort() != 0);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(new RequestOptions(requestOptions).setPort(server.actualPort()))
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(resp.statusCode(), 200);
          return resp.body();
        }))
      .onComplete(onSuccess(body -> {
        assertEquals(body.toString("UTF-8"), "hello");
        testComplete();
      }));
    await();
  }

  @Test
  public void testClientRequestOptionsSocketAddressOnly() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    Integer port = requestOptions.getPort();
    String host = requestOptions.getHost();
    server
      .requestHandler(request -> {
        assertEquals(host, request.authority().host());
        assertEquals((int)port, request.authority().port());
        request.response().end();
      });
    startServer(testAddress);
    SocketAddress server = SocketAddress.inetSocketAddress(port, host);
    client.request(new RequestOptions().setServer(server)).compose(req -> req.send().compose(resp -> {
      assertEquals(200, resp.statusCode());
      return resp.body();
    })).onComplete(onSuccess(body -> {
      testComplete();
    }));
    await();
  }

  /*
  @Test
  public void testRequestNPE() {
    String uri = "/some-uri?foo=bar";
    TestUtils.assertNullPointerException(() -> client.request(null, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri));
    TestUtils.assertNullPointerException(() -> client.request( null, 8080, "localhost", "/somepath"));
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, 8080, null, "/somepath"));
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, 8080, "localhost", null));
  }
*/

  @Test
  public void testInvalidAbsoluteURI() {
    try {
      client.request(new RequestOptions().setAbsoluteURI("ijdijwidjqwoijd192d192192ej12d"));
      fail("Should throw exception");
    } catch (VertxException e) {
      //OK
    }
  }

  @Test
  public void testPutHeadersOnRequest() throws Exception {
    server.requestHandler(req -> {
      assertEquals("bar", req.headers().get("foo"));
      assertEquals("bar", req.getHeader("foo"));
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .putHeader("foo", "bar")
        .send()
        .map(HttpClientResponse::statusCode))
      .onComplete(onSuccess(sc -> {
        assertEquals(200, (int) sc);
        testComplete();
      }));
    await();
  }

  @Test
  public void testPutHeaderReplacesPreviousHeaders() throws Exception {
    server.requestHandler(req ->
      req.response()
        .putHeader("Location", "http://example1.org")
        .putHeader("location", "http://example2.org")
        .send());
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .map(resp -> resp.headers().get("LocatioN")))
      .onComplete(onSuccess(header -> {
        assertEquals("http://example2.org", header);
        testComplete();
      }));
    await();
  }

  @Test
  public void testSimpleGET() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.GET, resp -> testComplete());
  }

  @Test
  public void testSimplePUT() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PUT, resp -> testComplete());
  }

  @Test
  public void testSimplePOST() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.POST, resp -> testComplete());
  }

  @Test
  public void testSimpleDELETE() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.DELETE, resp -> testComplete());
  }

  @Test
  public void testSimpleHEAD() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.HEAD, resp -> testComplete());
  }

  @Test
  public void testSimpleTRACE() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.TRACE, resp -> testComplete());
  }

  @Test
  public void testSimpleCONNECT() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.CONNECT, resp -> testComplete());
  }

  @Test
  public void testSimpleOPTIONS() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.OPTIONS, resp -> testComplete());
  }

  @Test
  public void testSimplePATCH() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PATCH, resp -> testComplete());
  }

  @Test
  public void testSimpleGETAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.GET, true, resp -> testComplete());
  }

  @Test
  public void testEmptyPathGETAbsolute() throws Exception {
    String uri = "";
    testSimpleRequest(uri, HttpMethod.GET, true, resp -> testComplete());
  }

  @Test
  public void testNoPathButQueryGETAbsolute() throws Exception {
    String uri = "?foo=bar";
    testSimpleRequest(uri, HttpMethod.GET, true, resp -> testComplete());
  }

  @Test
  public void testSimplePUTAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PUT, true, resp -> testComplete());
  }

  @Test
  public void testSimplePOSTAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.POST, true, resp -> testComplete());
  }

  @Test
  public void testSimpleDELETEAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.DELETE, true, resp -> testComplete());
  }

  @Test
  public void testSimpleHEADAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.HEAD, true, resp -> testComplete());
  }

  @Test
  public void testSimpleTRACEAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.TRACE, true, resp -> testComplete());
  }

  @Test
  public void testSimpleCONNECTAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.CONNECT, true, resp -> testComplete());
  }

  @Test
  public void testSimpleOPTIONSAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.OPTIONS, true, resp -> testComplete());
  }

  @Test
  public void testSimplePATCHAbsolute() throws Exception {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PATCH, true, resp -> testComplete());
  }

  private void testSimpleRequest(String uri, HttpMethod method, Handler<HttpClientResponse> handler) throws Exception {
    testSimpleRequest(uri, method, false, handler);
  }

  private void testSimpleRequest(String uri, HttpMethod method, boolean absolute, Handler<HttpClientResponse> handler) throws Exception {
    boolean ssl = this instanceof Http2Test || this instanceof Http3Test;
    RequestOptions options;
    if (absolute) {
      options = new RequestOptions(requestOptions).setServer(testAddress).setMethod(method).setAbsoluteURI((ssl ? "https://" : "http://") + DEFAULT_HTTP_HOST_AND_PORT + uri);
    } else {
      options = new RequestOptions(requestOptions).setMethod(method).setURI(uri);
    }
    testSimpleRequest(uri, method, options, absolute, handler);
  }

  private void testSimpleRequest(String uri, HttpMethod method, RequestOptions requestOptions, boolean absolute, Handler<HttpClientResponse> handler) throws Exception {
    int index = uri.indexOf('?');
    String query;
    String path;
    if (index == -1) {
      path = uri;
      query = null;
    } else {
      path = uri.substring(0, index);
      query = uri.substring(index + 1);
    }
    String resource = absolute && path.isEmpty() ? "/" + path : path;
    server.requestHandler(req -> {
      String expectedPath = req.method() == HttpMethod.CONNECT && HttpVersion.isFrameBased(req.version()) ? null :
        resource;
      String expectedQuery = req.method() == HttpMethod.CONNECT && HttpVersion.isFrameBased(req.version()) ? null :
        query;
      assertEquals(expectedPath, req.path());
      assertEquals(method, req.method());
      assertEquals(expectedQuery, req.query());
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(handler::handle));
    await();
  }

  @Test
  public void testServerChaining() throws Exception {
    server.requestHandler(req -> {
      assertTrue(req.response().setChunked(true) == req.response());
      testComplete();
    });

    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::send));

    await();
  }

  @Test
  public void testResponseEndHandlers1() throws Exception {
    waitFor(2);
    AtomicInteger cnt = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().putHeader("removedheader", "foo");
      req.response().headersEndHandler(v -> {
        // Insert another header
        req.response().putHeader("extraheader", "wibble");
        req.response().headers().remove("removedheader");
        assertEquals(0, cnt.getAndIncrement());
      });
      req.response().bodyEndHandler(v -> {
        assertEquals(0, req.response().bytesWritten());
        assertEquals(1, cnt.getAndIncrement());
        complete();
      });
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .map(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals("wibble", resp.headers().get("extraheader"));
          assertNull(resp.headers().get("removedheader"));
          return null;
        }))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testResponseEndHandlers2() throws Exception {
    waitFor(2);
    AtomicInteger cnt = new AtomicInteger();
    String content = "blah";
    server.requestHandler(req -> {
      req.response().headersEndHandler(v -> {
        // Insert another header
        req.response().putHeader("extraheader", "wibble");
        assertEquals(0, cnt.getAndIncrement());
      });
      req.response().bodyEndHandler(v -> {
        assertEquals(content.length(), req.response().bytesWritten());
        assertEquals(1, cnt.getAndIncrement());
        complete();
      });
      req.response().end(content);
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals("wibble", resp.headers().get("extraheader"));
          return resp.body();
        }))
      .onComplete(onSuccess(body -> {
        assertEquals(Buffer.buffer(content), body);
        complete();
      }));
    await();
  }

  @Test
  public void testResponseEndHandlersChunkedResponse() throws Exception {
    waitFor(2);
    AtomicInteger cnt = new AtomicInteger();
    String chunk = "blah";
    int numChunks = 6;
    StringBuilder content = new StringBuilder(chunk.length() * numChunks);
    IntStream.range(0, numChunks).forEach(i -> content.append(chunk));
    server.requestHandler(req -> {
      req.response().headersEndHandler(v -> {
        // Insert another header
        req.response().putHeader("extraheader", "wibble");
        assertEquals(0, cnt.getAndIncrement());
      });
      req.response().bodyEndHandler(v -> {
        assertEquals(content.length(), req.response().bytesWritten());
        assertEquals(1, cnt.getAndIncrement());
        complete();
      });
      req.response().setChunked(true);
      // note that we have a -1 here because the last chunk is written via end(chunk)
      IntStream.range(0, numChunks - 1).forEach(x -> req.response().write(chunk));
      // End with a chunk to ensure size is correctly calculated
      req.response().end(chunk);
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
      .send()
      .compose(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("wibble", resp.headers().get("extraheader"));
        return resp.body();
      })).onComplete(onSuccess(body -> {
      assertEquals(Buffer.buffer(content.toString()), body);
      complete();
    }));
    await();
  }

  @Test
  public void testResponseEndHandlersSendFile() throws Exception {
    waitFor(4);
    AtomicInteger cnt = new AtomicInteger();
    String content = "iqdioqwdqwiojqwijdwqd";
    File toSend = setupFile("somefile.txt", content);
    server.requestHandler(req -> {
      req.response().headersEndHandler(v -> {
        // Insert another header
        req.response().putHeader("extraheader", "wibble");
        assertEquals(0, cnt.getAndIncrement());
        complete();
      });
      req.response().bodyEndHandler(v -> {
        assertEquals(content.length(), req.response().bytesWritten());
        assertEquals(1, cnt.getAndIncrement());
        complete();
      });
      req.response().endHandler(v -> complete());
      req.response().sendFile(toSend.getAbsolutePath());
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
      .send()
      .compose(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("wibble", resp.headers().get("extraheader"));
        return resp.body();
      })).onComplete(onSuccess(body -> {
      assertEquals(Buffer.buffer(content), body);
      complete();
    }));
    await();
  }

  @Test
  public void testAbsoluteURI() throws Exception {
    String uri = "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/this/is/a/path/foo.html";
    testURIAndPath(uri, uri, "/this/is/a/path/foo.html");
  }

  @Test
  public void testRelativeURI() throws Exception {
    String uri = "/this/is/a/path/foo.html";
    testURIAndPath(uri, uri, uri);
  }

  @Test
  public void testAbsoluteURIWithHttpSchemaInQuery() throws Exception {
    String uri = "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/correct/path?url=http://localhost:8008/wrong/path";
    testURIAndPath(uri, uri, "/correct/path");
  }

  @Test
  public void testRelativeURIWithHttpSchemaInQuery() throws Exception {
    String uri = "/correct/path?url=http://localhost:8008/wrong/path";
    testURIAndPath(uri, uri, "/correct/path");
  }

  @Test
  public void testAbsoluteURIEmptyPath() throws Exception {
    String uri = "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/";
    testURIAndPath(uri, uri, "/");
  }

  @Test
  public void testEmptyURI() throws Exception {
    testURIAndPath("", "/", "/");
  }

  @Test
  public void testParams() throws Exception {
    server.requestHandler(req -> {
      MultiMap params1 = req.params(true);
      MultiMap params2 = req.params(true);
      assertSame(params1, params2); // got the cached value
      MultiMap params3 = req.params(false);
      assertNotSame(params1, params3); // cache refreshed
      MultiMap params4 = req.params(false);
      assertSame(params3, params4); // got the cached value

      assertEquals(params1.get("a"), "1;b=2");
      assertEquals(params3.get("a"), "1");

      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI("/?a=1;b=2&c=3"))
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end();
        }))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));
    await();
  }

  private void testURIAndPath(String uri, String expectedUri, String expectedPath) throws Exception {
    server.requestHandler(req -> {
      assertEquals(expectedUri, req.uri());
      assertEquals(expectedPath, req.path());
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI(uri))
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end();
        }))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testParamUmlauteDecoding() throws Exception {
    testParamDecoding("\u00e4\u00fc\u00f6");
  }

  @Test
  public void testParamPlusDecoding() throws Exception {
    testParamDecoding("+");
  }

  @Test
  public void testParamPercentDecoding() throws Exception {
    testParamDecoding("%");
  }

  @Test
  public void testParamSpaceDecoding() throws Exception {
    testParamDecoding(" ");
  }

  @Test
  public void testParamNormalDecoding() throws Exception {
    testParamDecoding("hello");
  }

  @Test
  public void testParamAltogetherDecoding() throws Exception {
    testParamDecoding("\u00e4\u00fc\u00f6+% hello");
  }

  private void testParamDecoding(String value) throws Exception {

    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.endHandler(v -> {
        MultiMap formAttributes = req.formAttributes();
        assertEquals(value, formAttributes.get("param"));
      });
      req.response().end();
    });
    Buffer body = Buffer.buffer("param=" + URLEncoder.encode(value,"UTF-8"));
    startServer(testAddress);
    client
      .request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .compose(req -> req
        .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
        .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED)
        .send(body).compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end();
        }))
      .onComplete(onSuccess(resp -> testComplete()));
    await();
  }

  @Test
  public void testParamsAmpersand() throws Exception {
    testParams('&');
  }

  @Test
  public void testParamsSemiColon() throws Exception {
    testParams(';');
  }

  private void testParams(char delim) throws Exception {
    MultiMap params = TestUtils.randomMultiMap(10);
    String query = generateQueryString(params, delim);
    server.requestHandler(req -> {
      assertEquals(query, req.query());
      assertEquals(params.size(), req.params().size());
      for (Map.Entry<String, String> entry : req.params()) {
        assertEquals(entry.getValue(), params.get(entry.getKey()));
      }
      req.response().end();
    });
    startServer(testAddress);
    sendAndAwait(new RequestOptions(requestOptions).setURI("some-uri/?" + query));
  }

  @Test
  public void testNoParams() throws Exception {
    server.requestHandler(req -> {
      assertNull(req.query());
      assertTrue(req.params().isEmpty());
      req.response().end();
    });
    startServer(testAddress);
    sendAndAwait(requestOptions);
  }

  @Test
  public void testOverrideParamsCharset() throws Exception {
    server.requestHandler(req -> {
      String val = req.getParam("param");
      assertEquals("\u20AC", val); // Euro sign
      req.setParamsCharset(StandardCharsets.ISO_8859_1.name());
      val = req.getParam("param");
      assertEquals("\u00E2\u0082\u00AC", val);
      req.response().end();
    });
    startServer(testAddress);
    sendAndAwait(new RequestOptions(requestOptions).setURI("/?param=%E2%82%AC"));
  }

  @Test
  public void testGetParamDefaultValue() throws Exception {
    String paramName1 = "foo";
    String paramName1Value = "bar";
    String paramName2 = "notPresentParam";
    String paramName2DefaultValue = "defaultValue";
    String reqUri = DEFAULT_TEST_URI + "/?" + paramName1 + "=" + paramName1Value;

    server.requestHandler(req -> {
      assertTrue(req.params().contains(paramName1));
      assertEquals(paramName1Value, req.getParam(paramName1, paramName2DefaultValue));
      assertFalse(req.params().contains(paramName2));
      assertEquals(paramName2DefaultValue, req.getParam(paramName2, paramName2DefaultValue));
      req.response().end();
    });
    startServer(testAddress);
    sendAndAwait(new RequestOptions(requestOptions).setURI(reqUri));
  }

  @Test
  public void testMissingContentTypeMultipartRequest() throws Exception {
    testInvalidMultipartRequest(null, HttpMethod.POST);
  }

  @Test
  public void testInvalidContentTypeMultipartRequest() throws Exception {
    testInvalidMultipartRequest("application/json", HttpMethod.POST);
  }

  @Test
  public void testInvalidMethodMultipartRequest() throws Exception {
    testInvalidMultipartRequest("multipart/form-data", HttpMethod.GET);
  }

  private void testInvalidMultipartRequest(String contentType, HttpMethod method) throws Exception {
    server.requestHandler(req -> {
      try {
        req.setExpectMultipart(true);
        fail();
      } catch (IllegalStateException ignore) {
        req.response().end();
      }
    });
    startServer(testAddress);
    sendAndAwait(requestOptions, req -> {
      if (contentType != null) {
        req.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
      }
    });
  }

  @Test
  public void testDefaultRequestHeaders() throws Exception {
    server.requestHandler(req -> {
      if (req.version() == HttpVersion.HTTP_1_1) {
        assertEquals(1, req.headers().size());
        assertEquals( DEFAULT_HTTP_HOST_AND_PORT, req.headers().get("host"));
      } else {
        assertEquals(0, req.headers().size());
      }
      req.response().end();
    });
    startServer(testAddress);
    sendAndAwait(requestOptions);
  }

  private void sendAndAwait(RequestOptions request) {
    sendAndAwait(request, req -> {});
  }

  private void sendAndAwait(RequestOptions request, Handler<HttpClientRequest> handler) {
    client.request(request)
      .compose(req -> {
        handler.handle(req);
        return req
          .send()
          .compose(resp -> {
            assertEquals(200, resp.statusCode());
            return resp.end();
          });
      })
      .onComplete(onSuccess(v -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testRequestHeadersWithCharSequence() throws Exception {
    HashMap<CharSequence, String> expectedHeaders = new HashMap<>();
    expectedHeaders.put(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf-8");
    expectedHeaders.put(HttpHeaderNames.CONTENT_ENCODING, "gzip");
    expectedHeaders.put(HttpHeaderNames.USER_AGENT, "Mozilla/5.0");

    server.requestHandler(req -> {

      MultiMap headers = req.headers();
      headers.remove("host");

      assertEquals(expectedHeaders.size(), headers.size());

      expectedHeaders.forEach((k, v) -> assertEquals(v, headers.get(k)));
      expectedHeaders.forEach((k, v) -> assertEquals(v, req.getHeader(k)));

      req.response().end();
    });
    startServer(testAddress);
    sendAndAwait(requestOptions, req -> {
      expectedHeaders.forEach((k, v) -> req.headers().set(k, v));
    });
  }

  @Test
  public void testRequestHeadersPutAll() throws Exception {
    testRequestHeaders(false);
  }

  @Test
  public void testRequestHeadersIndividually() throws Exception {
    testRequestHeaders(true);
  }

  private void testRequestHeaders(boolean individually) throws Exception {
    MultiMap expectedHeaders = randomMultiMap(10);

    server.requestHandler(req -> {
      MultiMap headers = req.headers();
      headers.remove("host");
      assertEquals(expectedHeaders.size(), expectedHeaders.size());
      for (Map.Entry<String, String> entry : expectedHeaders) {
        assertEquals(entry.getValue(), req.headers().get(entry.getKey()));
        assertEquals(entry.getValue(), req.getHeader(entry.getKey()));
      }
      req.response().end();
    });
    startServer(testAddress);
    sendAndAwait(requestOptions, req -> {
      if (individually) {
        for (Map.Entry<String, String> header : expectedHeaders) {
          req.headers().add(header.getKey(), header.getValue());
        }
      } else {
        req.headers().setAll(expectedHeaders);
      }
    });
  }

  @Test
  public void testResponseHeadersPutAll() throws Exception {
    testResponseHeaders(false);
  }

  @Test
  public void testResponseHeadersIndividually() throws Exception {
    testResponseHeaders(true);
  }

  private void testResponseHeaders(boolean individually) throws Exception {
    MultiMap headers = randomMultiMap(10);

    server.requestHandler(req -> {
      if (individually) {
        for (Map.Entry<String, String> header : headers) {
          req.response().headers().add(header.getKey(), header.getValue());
        }
      } else {
        req.response().headers().setAll(headers);
      }
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(resp -> resp.end().map(resp.headers())))
      .onComplete(onSuccess(respHeaders -> {
        assertTrue(headers.size() < respHeaders.size());
        for (Map.Entry<String, String> entry : headers) {
          assertEquals(entry.getValue(), respHeaders.get(entry.getKey()));
        }
        testComplete();
      }));
    await();
  }

  @Test
  public void testResponseHeadersWithCharSequence() throws Exception {
    HashMap<CharSequence, String> headers = new HashMap<>();
    headers.put(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf-8");
    headers.put(HttpHeaderNames.CONTENT_ENCODING, "gzip");
    headers.put(HttpHeaderNames.USER_AGENT, "Mozilla/5.0");

    server.requestHandler(req -> {
      headers.forEach((k, v) -> req.response().headers().add(k, v));
      req.response().end();
    });

    startServer(testAddress);

    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(resp -> resp.end().map(resp.headers())))
      .onComplete(onSuccess(respHeaders -> {
        assertTrue(headers.size() < respHeaders.size());
        headers.forEach((k, v) -> assertEquals(v, respHeaders.get(k)));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResponseMultipleSetCookieInHeader() throws Exception {
    testResponseMultipleSetCookie(true, false);
  }

  @Test
  public void testResponseMultipleSetCookieInTrailer() throws Exception {
    testResponseMultipleSetCookie(false, true);
  }

  @Test
  public void testResponseMultipleSetCookieInHeaderAndTrailer() throws Exception {
    testResponseMultipleSetCookie(true, true);
  }

  private void testResponseMultipleSetCookie(boolean inHeader, boolean inTrailer) throws Exception {
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

    startServer(testAddress);

    client.request(requestOptions).compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end().map(v -> resp.cookies());
        }))
      .onComplete(onSuccess(respCookies -> {
        assertEquals(cookies.size(), respCookies.size());
        for (int i = 0; i < cookies.size(); ++i) {
          assertEquals(cookies.get(i), respCookies.get(i));
        }
        testComplete();
      }));

    await();
  }

  @Test
  public void testUseRequestAfterComplete() throws Exception {
    server.requestHandler(noOpHandler());

    startServer(testAddress);

    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.end();

      Buffer buff = Buffer.buffer();
      assertIllegalStateExceptionAsync(() -> req.end());
      assertIllegalStateException(() -> req.continueHandler(noOpHandler()));
      assertIllegalStateException(() -> req.drainHandler(noOpHandler()));
      assertIllegalStateExceptionAsync(() -> req.end("foo"));
      assertIllegalStateExceptionAsync(() -> req.end(buff));
      assertIllegalStateExceptionAsync(() -> req.end("foo", "UTF-8"));
      assertIllegalStateException(() -> req.sendHead());
      assertIllegalStateException(() -> req.setChunked(false));
      assertIllegalStateException(() -> req.setWriteQueueMaxSize(123));
      assertIllegalStateExceptionAsync(() -> req.write(buff));
      assertIllegalStateExceptionAsync(() -> req.write("foo"));
      assertIllegalStateExceptionAsync(() -> req.write("foo", "UTF-8"));
      assertIllegalStateExceptionAsync(() -> req.write(buff));
      assertIllegalStateException(() -> req.writeQueueFull());

      testComplete();
    }));

    await();
  }

  @Test
  public void testRequestBodyBufferAtEnd() throws Exception {
    Buffer body = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> req.bodyHandler(buffer -> {
      assertEquals(body, buffer);
      req.response().end();
    }));

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .compose(req -> req
        .send(body)
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end();
        }))
      .onComplete(onSuccess(req -> testComplete()));
    await();
  }

  @Test
  public void testRequestBodyStringDefaultEncodingAtEnd() throws Exception {
    testRequestBodyStringAtEnd(null);
  }

  @Test
  public void testRequestBodyStringUTF8AtEnd() throws Exception {
    testRequestBodyStringAtEnd("UTF-8");
  }

  @Test
  public void testRequestBodyStringUTF16AtEnd() throws Exception {
    testRequestBodyStringAtEnd("UTF-16");
  }

  private void testRequestBodyStringAtEnd(String encoding) throws Exception {
    String body = TestUtils.randomUnicodeString(1000);
    Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = Buffer.buffer(body);
    } else {
      bodyBuff = Buffer.buffer(body, encoding);
    }

    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(bodyBuff, buffer);
        testComplete();
      });
    });

    startServer(testAddress);

    client
      .request(requestOptions)
      .compose(req -> {
        if (encoding == null) {
          return req.end(body);
        } else {
          return req.end(body, encoding);
        }
      });

    await();
  }

  @Test
  public void testRequestBodyWriteChunked() throws Exception {
    testRequestBodyWrite(true);
  }

  @Test
  public void testRequestBodyWriteNonChunked() throws Exception {
    testRequestBodyWrite(false);
  }

  private void testRequestBodyWrite(boolean chunked) throws Exception {
    Buffer body = Buffer.buffer();

    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(body, buffer);
        req.response().end();
      });
    });

    startServer(testAddress);

    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .compose(req -> {
        int numWrites = 10;
        int chunkSize = 100;
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
        return req.response().compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end();
        });
      }).onComplete(onSuccess(req -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testRequestBodyWriteStringChunkedDefaultEncoding() throws Exception {
    testRequestBodyWriteString(true, null);
  }

  @Test
  public void testRequestBodyWriteStringChunkedUTF8() throws Exception {
    testRequestBodyWriteString(true, "UTF-8");
  }

  @Test
  public void testRequestBodyWriteStringChunkedUTF16() throws Exception {
    testRequestBodyWriteString(true, "UTF-16");
  }

  @Test
  public void testRequestBodyWriteStringNonChunkedDefaultEncoding() throws Exception {
    testRequestBodyWriteString(false, null);
  }

  @Test
  public void testRequestBodyWriteStringNonChunkedUTF8() throws Exception {
    testRequestBodyWriteString(false, "UTF-8");
  }

  @Test
  public void testRequestBodyWriteStringNonChunkedUTF16() throws Exception {
    testRequestBodyWriteString(false, "UTF-16");
  }

  private void testRequestBodyWriteString(boolean chunked, String encoding) throws Exception {
    String body = TestUtils.randomUnicodeString(1000);
    Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = Buffer.buffer(body);
    } else {
      bodyBuff = Buffer.buffer(body, encoding);
    }

    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        assertEquals(bodyBuff, buff);
        testComplete();
      });
    });

    startServer(testAddress);

    client
      .request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
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
  public void testRequestWrite() throws Exception {
    int times = 3;
    Buffer chunk = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        Buffer expected = Buffer.buffer();
        for (int i = 0;i < times;i++) {
          expected.appendBuffer(chunk);
        }
        assertEquals(expected, buff);
        testComplete();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
        req.setChunked(true);
        int padding = 5;
        for (int i = 0;i < times;i++) {
          Buffer paddedChunk = TestUtils.leftPad(padding, chunk);
          req.write(paddedChunk);
        }
        req.end();
      }));
    await();
  }

  @Repeat(times = 10)
  @Test
  public void testClientExceptionHandlerCalledWhenServerTerminatesConnection() throws Exception {
    int numReqs = 10;
    waitFor(numReqs);
    server.requestHandler(request -> {
      request.connection().close();
    });
    startServer(testAddress);
    // Exception handler should be called for any requests in the pipeline if connection is closed
    for (int i = 0; i < numReqs; i++) {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onFailure(err -> complete()));
    }
    await();
  }

  @Test
  public void testClientExceptionHandlerCalledWhenServerTerminatesConnectionAfterPartialResponse() throws Exception {
    server.requestHandler(request -> {
      //Write partial response then close connection before completing it
      HttpServerResponse resp = request.response().setChunked(true);
      resp.write("foo");
      request.connection().close();
    });
    startServer(testAddress);
    // Exception handler should be called for any requests in the pipeline if connection is closed
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp ->
        resp.exceptionHandler(atMostOnce(t -> {
          testComplete();
        })))
      );
    }));
    await();
  }

  @Test
  public void testContextExceptionHandlerCalledWhenExceptionOnDataHandler() throws Exception {
    client.close();
    server.requestHandler(request -> {
      request.response().end("foo");
    });
    startServer(testAddress);
    // Exception handler should be called for any exceptions in the data handler
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      RuntimeException cause = new RuntimeException("should be caught");
      ctx.exceptionHandler(err -> {
        if (err == cause) {
          testComplete();
        }
      });
      client = vertx.createHttpClient(createBaseClientOptions());
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.handler(data -> {
            throw cause;
          });
        }));
      }));
    });
    await();
  }

  @Test
  public void testClientExceptionHandlerCalledWhenExceptionOnBodyHandler() throws Exception {
    client.close();
    server.requestHandler(request -> {
      request.response().end("foo");
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    // Exception handler should be called for any exceptions in the data handler
    Context ctx = vertx.getOrCreateContext();
    RuntimeException cause = new RuntimeException("should be caught");
    ctx.runOnContext(v -> {
      ctx.exceptionHandler(err -> {
        if (err == cause) {
          testComplete();
        }
      });
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.bodyHandler(data -> {
            throw cause;
          });
        }));
      }));
    });
    await();
  }

  @Test
  public void testNoExceptionHandlerCalledWhenResponseEnded() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      req.exceptionHandler(this::fail);
      resp.exceptionHandler(err -> {
        fail(err);
      });
      resp.end();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .exceptionHandler(t -> {
          fail("Should not be called");
        })
        .send().onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            vertx.setTimer(100, tid -> testComplete());
          });
          resp.exceptionHandler(t -> {
            fail("Should not be called");
          });
        }));
    }));
    await();
  }

  @Test
  public void testServerExceptionHandlerOnClose() {
    waitFor(3);
    vertx.createHttpServer().requestHandler(req -> {
      HttpServerResponse resp = req.response();
      AtomicInteger reqExceptionHandlerCount = new AtomicInteger();
      AtomicInteger respExceptionHandlerCount = new AtomicInteger();
      AtomicInteger respEndHandlerCount = new AtomicInteger();
      req.exceptionHandler(err -> {
        assertEquals(1, reqExceptionHandlerCount.incrementAndGet());
        assertEquals(1, respExceptionHandlerCount.get());
        assertEquals(1, respEndHandlerCount.get());
        assertTrue(resp.closed());
        assertFalse(resp.ended());
        try {
          resp.end();
        } catch (IllegalStateException ignore) {
          // Expected
        }
      });
      resp.exceptionHandler(err -> {
        assertEquals(0, reqExceptionHandlerCount.get());
        assertEquals(1, respExceptionHandlerCount.incrementAndGet());
        assertEquals(0, respEndHandlerCount.get());
        complete();
      });
      resp.endHandler(v -> {
        assertEquals(0, reqExceptionHandlerCount.get());
        assertEquals(1, respExceptionHandlerCount.get());
        assertEquals(1, respEndHandlerCount.incrementAndGet());
        complete();
      });
      req.connection().closeHandler(v -> {
        assertEquals(1, reqExceptionHandlerCount.get());
        assertEquals(1, respExceptionHandlerCount.get());
        assertEquals(1, respEndHandlerCount.get());
        complete();
      });
    }).listen(testAddress).onComplete(onSuccess(ar -> {
      HttpClient client = vertx.createHttpClient();
      client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
        .onComplete(onSuccess(req -> {
          req.setChunked(true);
          req.sendHead().onComplete(v -> {
            req.connection().close();
          });
      }));
    }));
    await();
  }

  @Test
  public void testClientRequestExceptionHandlerCalledWhenConnectionClosed() throws Exception {
    server.requestHandler(req -> {
      req.handler(buff -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
      req.setChunked(true);
      req.exceptionHandler(err -> {
        testComplete();
      });
      req.write("chunk");
    }));
    await();
  }

  @Test
  public void testClientResponseExceptionHandlerCalledWhenConnectionClosed() throws Exception {
    AtomicReference<HttpConnection> conn = new AtomicReference<>();
    server.requestHandler(req -> {
      conn.set(req.connection());
      req.response().setChunked(true).write("chunk");
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.handler(buff -> {
          conn.get().close();
        });
        resp.exceptionHandler(atMostOnce(err -> {
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testClientRequestExceptionHandlerCalledWhenRequestEnded() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req
        .exceptionHandler(this::fail)
        .send().onComplete(onFailure(err -> {
          complete();
        }));
      try {
        req.exceptionHandler(err -> fail());
        fail();
      } catch (Exception e) {
        complete();
      }
    }));
    await();
  }

  @Test
  public void testDefaultStatus() throws Exception {
    testStatusCode(-1, null);
  }

  @Test
  public void testDefaultOther() throws Exception {
    // Doesn't really matter which one we choose
    testStatusCode(405, null);
  }

  @Test
  public void testOverrideStatusMessage() throws Exception {
    testStatusCode(404, "some message");
  }

  @Test
  public void testOverrideDefaultStatusMessage() throws Exception {
    testStatusCode(-1, "some other message");
  }

  private void testStatusCode(int code, String statusMessage) throws Exception {
    server.requestHandler(req -> {
      if (code != -1) {
        req.response().setStatusCode(code);
      }
      if (statusMessage != null) {
        req.response().setStatusMessage(statusMessage);
      }
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
      .send()
      .compose(resp -> {
        int theCode;
        if (code == -1) {
          // Default code - 200
          assertEquals(200, resp.statusCode());
          theCode = 200;
        } else {
          theCode = code;
        }
        if (statusMessage != null && !HttpVersion.isFrameBased(resp.version())) {
          assertEquals(statusMessage, resp.statusMessage());
        } else {
          assertEquals(HttpResponseStatus.valueOf(theCode).reasonPhrase(), resp.statusMessage());
        }
        return resp.end();
      })).onComplete(onSuccess(v -> {
      testComplete();
    }));

    await();
  }

  @Test
  public void testResponseTrailersPutAll() throws Exception {
    testResponseTrailers(false);
  }

  @Test
  public void testResponseTrailersPutIndividually() throws Exception {
    testResponseTrailers(true);
  }

  private void testResponseTrailers(boolean individually) throws Exception {
    MultiMap trailers = randomMultiMap(10);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      if (individually) {
        for (Map.Entry<String, String> header : trailers) {
          req.response().trailers().add(header.getKey(), header.getValue());
        }
      } else {
        req.response().trailers().setAll(trailers);
      }
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end().map(v -> resp.trailers());
        }))
      .onComplete(onSuccess(respTrailers -> {
        assertEquals(trailers.size(), respTrailers.size());
        for (Map.Entry<String, String> entry : trailers) {
          assertEquals(entry.getValue(), respTrailers.get(entry.getKey()));
        }
        testComplete();
      }));
    await();
  }

  @Test
  public void testResponseNoTrailers() throws Exception {
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.end().map(v -> resp.trailers());
        })).onComplete(onSuccess(trailers -> {
        assertTrue(trailers.isEmpty());
        testComplete();
      }));

    await();
  }

  @Test
  public void testUseAfterServerResponseHeadSent() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader(HttpHeaders.CONTENT_LENGTH, "" + 128);
      resp.write("01234567");
      assertTrue(resp.headWritten());
      assertIllegalStateException(() -> resp.setChunked(false));
      assertIllegalStateException(() -> resp.setStatusCode(200));
      assertIllegalStateException(() -> resp.setStatusMessage("OK"));
      assertIllegalStateException(() -> resp.putHeader("foo", "bar"));
      assertIllegalStateException(() -> resp.addCookie(Cookie.cookie("the_cookie", "wibble")));
      assertIllegalStateException(() -> resp.removeCookie("the_cookie"));
      testComplete();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testUseAfterServerResponseSent() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      assertFalse(resp.ended());
      resp.end();
      assertTrue(resp.ended());
      Buffer buff = Buffer.buffer();
      assertIllegalStateException(() -> resp.drainHandler(noOpHandler()));
      assertIllegalStateException(() -> resp.exceptionHandler(noOpHandler()));
      assertIllegalStateException(() -> resp.setChunked(false));
      assertIllegalStateException(() -> resp.setWriteQueueMaxSize(123));
      assertIllegalStateException(() -> resp.writeQueueFull());
      assertIllegalStateException(() -> resp.putHeader("foo", "bar"));
      assertIllegalStateException(() -> resp.sendFile("webroot/somefile.html"));
      assertIllegalStateException(() -> resp.end());
      assertIllegalStateException(() -> resp.end("foo"));
      assertIllegalStateException(() -> resp.end(buff));
      assertIllegalStateException(() -> resp.end("foo", "UTF-8"));
      assertIllegalStateException(() -> resp.write(buff));
      assertIllegalStateException(() -> resp.write("foo"));
      assertIllegalStateException(() -> resp.write("foo", "UTF-8"));
      assertIllegalStateException(() -> resp.write(buff));
      assertIllegalStateException(() -> resp.sendFile("webroot/somefile.html"));
      assertIllegalStateException(() -> resp.end());
      assertIllegalStateException(() -> resp.end("foo"));
      assertIllegalStateException(() -> resp.end(buff));
      assertIllegalStateException(() -> resp.end("foo", "UTF-8"));
      assertIllegalStateException(() -> resp.write(buff));
      assertIllegalStateException(() -> resp.write("foo"));
      assertIllegalStateException(() -> resp.write("foo", "UTF-8"));
      assertIllegalStateException(() -> resp.write(buff));
      testComplete();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testSetInvalidStatusMessage() throws Exception {
    server.requestHandler(req -> {
      try {
        req.response().setStatusMessage("hello\nworld");
        assertEquals(serverAlpnProtocolVersion(), req.version());
      } catch (IllegalArgumentException ignore) {
        assertEquals(HttpVersion.HTTP_1_1, req.version());
      }
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end)
      ).onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testResponseBodyBufferAtEnd() throws Exception {
    Buffer body = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().end(body);
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> assertEquals(body, buff))))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testResponseBodyWriteChunked() throws Exception {
    testResponseBodyWrite(true);
  }

  @Test
  public void testResponseBodyWriteNonChunked() throws Exception {
    testResponseBodyWrite(false);
  }

  private void testResponseBodyWrite(boolean chunked) throws Exception {
    Buffer body = Buffer.buffer();

    int numWrites = 10;
    int chunkSize = 100;

    server.requestHandler(req -> {
      assertFalse(req.response().headWritten());
      if (chunked) {
        req.response().setChunked(true);
      } else {
        req.response().headers().set("Content-Length", String.valueOf(numWrites * chunkSize));
      }
      assertFalse(req.response().headWritten());
      for (int i = 0; i < numWrites; i++) {
        Buffer b = TestUtils.randomBuffer(chunkSize);
        body.appendBuffer(b);
        req.response().write(b);
        assertTrue(req.response().headWritten());
      }
      req.response().end();
      assertTrue(req.response().headWritten());
    });

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> assertEquals(body, buff))))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testResponseBodyWriteStringChunkedDefaultEncoding() throws Exception {
    testResponseBodyWriteString(true, null);
  }

  @Test
  public void testResponseBodyWriteStringChunkedUTF8() throws Exception {
    testResponseBodyWriteString(true, "UTF-8");
  }

  @Test
  public void testResponseBodyWriteStringChunkedUTF16() throws Exception {
    testResponseBodyWriteString(true, "UTF-16");
  }

  @Test
  public void testResponseBodyWriteStringNonChunkedDefaultEncoding() throws Exception {
    testResponseBodyWriteString(false, null);
  }

  @Test
  public void testResponseBodyWriteStringNonChunkedUTF8() throws Exception {
    testResponseBodyWriteString(false, "UTF-8");
  }

  @Test
  public void testResponseBodyWriteStringNonChunkedUTF16() throws Exception {
    testResponseBodyWriteString(false, "UTF-16");
  }

  private void testResponseBodyWriteString(boolean chunked, String encoding) throws Exception {
    String body = TestUtils.randomUnicodeString(1000);
    Buffer bodyBuff;

    if (encoding == null) {
      bodyBuff = Buffer.buffer(body);
    } else {
      bodyBuff = Buffer.buffer(body, encoding);
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

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> assertEquals(body, encoding == null ? buff.toString() : buff.toString(encoding)))))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testResponseWrite() throws Exception {
    Buffer body = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> assertEquals(body, buff))))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  @DetectFileDescriptorLeaks
  public void testSendFile() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, false,
      () -> client.request(requestOptions));
  }

  @Test
  public void testSendFileWithHandler() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, true,
      () -> client.request(requestOptions));
  }

  protected void sendFile(String fileName, String contentExpected, boolean useHandler, Supplier<Future<HttpClientRequest>> requestFact) throws Exception {
    waitFor(2);
    File fileToSend = setupFile(fileName, contentExpected);
    server.requestHandler(req -> {
      if (useHandler) {
        req.response().sendFile(fileToSend.getAbsolutePath()).onComplete(onSuccess(v -> complete()));
      } else {
        req.response().sendFile(fileToSend.getAbsolutePath());
        complete();
      }
    });
    startServer(testAddress);
    requestFact.get().compose(req -> req
        .send()
        .expecting(that(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals("text/html", resp.headers().get("Content-Type"));
          assertEquals(fileToSend.length(), Long.parseLong(resp.headers().get("content-length")));
          resp.exceptionHandler(this::fail);
        }))
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(buff -> {
        assertEquals(contentExpected, buff.toString());
        complete();
      }));
    await();
  }

  @Test
  public void testSendNonExistingFile() throws Exception {
    server.requestHandler(req -> {
      final Context ctx = vertx.getOrCreateContext();
      req.response().sendFile("/not/existing/path").onComplete(event -> {
        assertEquals(ctx, vertx.getOrCreateContext());
        if (event.failed()) {
          req.response().end("failed");
        }
      });
    });

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> assertEquals("failed", buff.toString()))))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testSendFileOverrideHeaders() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile("test-send-file.html", content);

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(file.getAbsolutePath());
    });

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(that(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(file.length(), Long.parseLong(resp.headers().get("content-length")));
          assertEquals("wibble", resp.headers().get("content-type"));
        }))
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> assertEquals(content, buff.toString()))))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testSendFileNotFound() throws Exception {
    waitFor(2);

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile("nosuchfile.html").onComplete(onFailure(v -> complete()));
    });

    startServer(testAddress);
    AtomicBoolean completed = new AtomicBoolean();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(ar -> {
        if (!completed.get()) {
          fail();
        }
      });
    }));
    vertx.setTimer(100, tid -> {
      completed.set(true);
      complete();
    });

    await();
  }

  @Test
  public void testSendFileDirectoryWithHandler() throws Exception {

    File dir = testFolder.newFolder();

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(dir.getAbsolutePath())
        .onComplete(onFailure(t -> {
          assertTrue(t instanceof FileNotFoundException);
          testComplete();
        }));
    });

    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onFailure(err -> {}));
    }));

    await();
  }

  @Test
  public void testSendOpenRangeFileFromClasspath() throws Exception {
    server.requestHandler(res -> {
      res.response().sendFile("hosts_config.txt", 13);
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .expecting(that(resp -> assertEquals(String.valueOf(10), resp.headers().get("Content-Length"))))
        .compose(HttpClientResponse::body)
        .onComplete(onSuccess(body -> {
          assertTrue(body.toString().startsWith("server.net"));
          assertEquals(10, body.toString().length());
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testSendRangeFileFromClasspath() throws Exception {
    server.requestHandler(res -> {
      res.response().sendFile("hosts_config.txt", 13, 10);
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(that(resp -> assertEquals(String.valueOf(10), resp.headers().get("Content-Length"))))
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(body -> {
        assertEquals("server.net", body.toString());
        assertEquals(10, body.toString().length());
        testComplete();
      }));
    await();
  }

  @Test
  public void testSendZeroRangeFile() throws Exception {
    File f = setupFile("twenty_three_bytes.txt", TestUtils.randomAlphaString(23));
    server.requestHandler(res -> res.response().sendFile(f.getAbsolutePath(), 23, 0));
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(that(resp -> assertEquals(String.valueOf(0), resp.headers().get("Content-Length"))))
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(body -> {
        assertEquals("", body.toString());
        assertEquals(0, body.toString().length());
        testComplete();
      }));
    await();
  }

  @Test
  public void testSendFileOffsetIsHigherThanFileLength() throws Exception {
    testSendFileWithFailure(
      (resp, f) -> resp.sendFile(f.getAbsolutePath(), 33, 10),
      err -> assertEquals("offset : 33 is larger than the requested file length : 23", err.getMessage()));
  }

  @Test
  public void testSendFileWithNegativeLength() throws Exception {
    testSendFileWithFailure((resp, f) -> resp.sendFile(f.getAbsolutePath(), 0, -100), err -> {
      assertEquals("length : -100 (expected: >= 0)", err.getMessage());
    });
  }

  @Test
  public void testSendFileWithNegativeOffset() throws Exception {
    testSendFileWithFailure((resp, f) -> resp.sendFile(f.getAbsolutePath(), -100, 23), err -> {
      assertEquals("offset : -100 (expected: >= 0)", err.getMessage());
    });
  }

  private void testSendFileWithFailure(BiFunction<HttpServerResponse, File, Future<Void>> sendFile, Consumer<Throwable> checker) throws Exception {
    waitFor(2);
    File f = setupFile("twenty_three_bytes.txt", TestUtils.randomAlphaString(23));
    server.requestHandler(res -> {
        // Expected
        sendFile
        .apply(res.response(), f)
        .andThen(onFailure(checker::accept))
        .recover(v -> res.response().setStatusCode(500).end())
        .onComplete(onSuccess(v -> {
          complete();
        }));
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(response -> {
        assertEquals(500, response.statusCode());
        complete();
      }));

    await();
  }

  @Test
  public void test100ContinueHandledAutomatically() throws Exception {
    Buffer toSend = TestUtils.randomBuffer(1000);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setHandle100ContinueAutomatically(true));

    server.requestHandler(req -> {
      req.bodyHandler(data -> {
        assertEquals(toSend, data);
        req.response().end();
      });
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req
        .response()
        .onComplete(onSuccess(resp -> resp.endHandler(v -> testComplete())));
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
  public void test100ContinueHandledManually() throws Exception {

    Buffer toSend = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> {
      assertEquals("100-continue", req.getHeader("expect"));
      req.response().writeContinue();
      req.bodyHandler(data -> {
        assertEquals(toSend, data);
        req.response().end();
      });
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onSuccess(resp -> {
          resp.endHandler(v -> testComplete());
        }));
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
  public void test100ContinueRejectedManually() throws Exception {

    server.requestHandler(req -> {
      req.response().setStatusCode(405).end();
      req.bodyHandler(data -> {
        fail("body should not be received");
      });
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onSuccess(resp -> {
          assertEquals(405, resp.statusCode());
          testComplete();
        }));
      req.headers().set("Expect", "100-continue");
      req.setChunked(true);
      req.continueHandler(v -> {
        fail("should not be called");
      });
      req.sendHead();
    }));

    await();
  }

  @Test
  public void test100ContinueTimeout() throws Exception {

    waitFor(2);

    server.requestHandler(req -> {
      req.response().writeContinue();
    });

    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setIdleTimeout(1));

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
        req
          .putHeader("Expect", "100-continue")
          .continueHandler(v -> complete())
          .send()
          .onComplete(onFailure(err -> complete()));
    }));
    await();
  }

  @Test
  public void test103EarlyHints() throws Exception {

    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      req.pause();
      resp.writeEarlyHints(HeadersMultiMap.httpHeaders().add("wibble", "wibble-103-value"))
        .onComplete(onSuccess(result -> {
          req.resume();
          resp.putHeader("wibble", "wibble-200-value");
          req.bodyHandler(body -> {
            assertEquals("request-body", body.toString());
            resp.end("response-body");
          });
      }));
    });

    AtomicBoolean earlyHintsHandled = new AtomicBoolean();

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
        req
          .earlyHintsHandler(earlyHintsHeaders -> {
            assertEquals("wibble-103-value", earlyHintsHeaders.get("wibble"));
            earlyHintsHandled.set(true);
          })
          .send("request-body")
          .onComplete(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
            assertEquals("wibble-200-value", resp.headers().get("wibble"));
            resp.endHandler(v -> {
              assertEquals("Early hints handle check", true, earlyHintsHandled.get());
              testComplete();
            }).bodyHandler(body -> assertEquals(body, Buffer.buffer("response-body")));
          }));
      }));
    await();
  }

  @Test
  public void testClientDrainHandler() throws Exception {
    pausingServer(resumeFuture -> {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.setChunked(true);
        assertFalse(req.writeQueueFull());
        req.setWriteQueueMaxSize(1000);
        Buffer buff = TestUtils.randomBuffer(10000);
        vertx.setPeriodic(1, id -> {
          req.write(buff);
          if (req.writeQueueFull()) {
            vertx.cancelTimer(id);
            req.drainHandler(v -> {
              assertFalse(req.writeQueueFull());
              testComplete();
            });

            // Tell the server to resume
            resumeFuture.complete();
          }
        });
      }));
    });

    await();
  }

  private void pausingServer(Consumer<Promise<Void>> consumer) throws Exception {
    Promise<Void> resumeFuture = Promise.promise();
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.pause();
      Context ctx = vertx.getOrCreateContext();
      resumeFuture.future().onComplete(v1 -> {
        ctx.runOnContext(v2 -> {
          req.resume();
        });
      });
      req.handler(buff -> {
        req.response().write(buff);
      });
    });

    startServer(testAddress);
    consumer.accept(resumeFuture);
  }

  @Test
  public void testServerDrainHandler() throws Exception {
    drainingServer(resumeFuture -> {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
        resp.pause();
        resumeFuture.onComplete(ar -> resp.resume());
      }));
    });

    await();
  }

  private void drainingServer(Consumer<Future<Void>> consumer) throws Exception {

    Promise<Void> resumeFuture = Promise.promise();

    server.requestHandler(req -> {
      req.response().setChunked(true);
      assertFalse(req.response().writeQueueFull());
      req.response().setWriteQueueMaxSize(1000);

      Buffer buff = TestUtils.randomBuffer(10000);
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
          resumeFuture.complete();
        }
      });
    });

    startServer(testAddress);
    consumer.accept(resumeFuture.future());
  }

  @Test
  public void testConnectInvalidPort() {
    client.request(HttpMethod.GET, 9998, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).onComplete(onFailure(err -> complete()));
    await();
  }

  @Test
  public void testConnectInvalidHost() {
    client.request(HttpMethod.GET, 9998, "255.255.255.255", DEFAULT_TEST_URI).onComplete(onFailure(resp -> complete()));
    await();
  }

  @Test
  public void testSetHandlersAfterListening() throws Exception {
    server.requestHandler(noOpHandler());

    startServer(testAddress);

    assertIllegalStateException(() -> server.requestHandler(noOpHandler()));
    assertIllegalStateException(() -> server.webSocketHandler(noOpHandler()));
  }

  @Test
  public void testListenNoHandlers() {
    assertIllegalStateException(() -> server.listen());
  }

  @Test
  public void testListenTwice() throws Exception {
    server.requestHandler(noOpHandler());
    startServer(testAddress);
    assertIllegalStateException(() -> server.listen());
  }

  @Test
  public void testHeadCanSetContentLength() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.HEAD, req.method());
      // Head never contains a body but it can contain a Content-Length header
      // Since headers from HEAD must correspond EXACTLY with corresponding headers for GET
      req.response().headers().set("Content-Length", String.valueOf(41));
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.HEAD))
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp.end().map(resp.headers().get("Content-Length"))))
      .onComplete(onSuccess(contentLength -> {
        assertEquals("41", contentLength);
        testComplete();
      }));
    await();
  }

  @Test
  public void testHeadDoesNotSetAutomaticallySetContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.HEAD, 200, HttpHeaders.headers());
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testHeadAllowsContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.HEAD, 200, HttpHeaders.set("content-length", "34"));
    assertEquals("34", respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testHeadRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.HEAD, 200, HttpHeaders.set("transfer-encoding", "chunked"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testNoContentRemovesContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 204, HttpHeaders.set("content-length", "34"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testNoContentRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 204, HttpHeaders.set("transfer-encoding", "chunked"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testResetContentSetsContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 205, HttpHeaders.headers());
    assertEquals("0", respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testResetContentRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 205, HttpHeaders.set("transfer-encoding", "chunked"));
    assertEquals("0", respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testNotModifiedDoesNotSetAutomaticallySetContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 304, HttpHeaders.headers());
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testNotModifiedAllowsContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 304, HttpHeaders.set("content-length", "34"));
    assertEquals("34", respHeaders.get("Content-Length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void testNotModifiedRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 304, HttpHeaders.set("transfer-encoding", "chunked"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void test1xxRemovesContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 102, HttpHeaders.set("content-length", "34"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  @Test
  public void test1xxRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 102, HttpHeaders.set("transfer-encoding", "chunked"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  protected MultiMap checkEmptyHttpResponse(HttpMethod method, int sc, MultiMap reqHeaders) throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setStatusCode(sc);
      reqHeaders.remove("transfer-encoding");
      resp.headers().addAll(reqHeaders);
      resp.end();
    });
    startServer(testAddress);
    try {
      Future<MultiMap> result = client
        .request(new RequestOptions(requestOptions).setMethod(method)).compose(req ->
          req
            .setFollowRedirects(false)
            .send()
            .compose(resp ->
              resp.body().compose(body -> {
                if (body.length() > 0) {
                  return Future.failedFuture(new Exception());
                } else {
                  return Future.succeededFuture(resp.headers());
                }
              })
            ));
      return result.await(20, TimeUnit.SECONDS);
    } finally {
      client.close();
    }
  }

  @Test
  public void testHeadHasNoContentLengthByDefault() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.HEAD, req.method());
      // By default HEAD does not have a content-length header
      req.response().end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.HEAD))
      .compose(req -> req
        .send()
        .expecting(that(resp -> assertNull(resp.headers().get(HttpHeaders.CONTENT_LENGTH))))
        .compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testHeadButCanSetContentLength() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.HEAD, req.method());
      // By default HEAD does not have a content-length header but it can contain a content-length header
      // if explicitly set
      req.response().putHeader(HttpHeaders.CONTENT_LENGTH, "41").end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.HEAD))
      .compose(req -> req
        .send()
        .expecting(that(resp -> assertEquals("41", resp.headers().get(HttpHeaders.CONTENT_LENGTH))))
        .compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testRemoteAddress() throws Exception {
    server.requestHandler(req -> {
      if (testAddress.isInetSocket()) {
        assertEquals("127.0.0.1", req.remoteAddress().host());
      } else {
        // Returns null for domain sockets
      }
      req.response().end();
    });

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .onComplete(onSuccess(req -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testGetAbsoluteURI() throws Exception {
    server.requestHandler(req -> {
      assertEquals(req.scheme() + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/foo/bar", req.absoluteURI());
      req.response().end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI("/foo/bar"))
      .compose(req -> req.send().compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testGetAbsoluteURIWithParam() throws Exception {
    server.requestHandler(req -> {
      assertEquals(req.scheme() + "://localhost:" + DEFAULT_HTTP_PORT + "/foo/bar?a=1", req.absoluteURI());
      req.response().end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI("/foo/bar?a=1"))
      .compose(req -> req.send().compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testGetAbsoluteURIWithUnsafeParam() throws Exception {
    server.requestHandler(req -> {
      assertEquals(req.scheme() + "://localhost:" + DEFAULT_HTTP_PORT + "/foo/bar?a={1}", req.absoluteURI());
      req.response().end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI("/foo/bar?a={1}"))
      .compose(req -> req.send().compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testGetAbsoluteURIWithOptionsServerLevel() throws Exception {
    server.requestHandler(req -> {
      assertEquals(OPTIONS, req.method());
      assertNull(req.absoluteURI());
      req.response().end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI("*").setMethod(OPTIONS))
      .compose(req -> req.send().compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testGetAbsoluteURIWithAbsoluteRequestUri() throws Exception {
    server.requestHandler(req -> {
      assertEquals("http://www.w3.org/pub/WWW/TheProject.html", req.absoluteURI());
      req.response().end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI("http://www.w3.org/pub/WWW/TheProject.html"))
      .compose(req -> req.send().compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));

    await();
  }

  @Test
  public void testListenInvalidPort() throws Exception {
    server.close();
    ServerSocket occupied = null;
    try{
      /* Ask to be given a usable port, then use it exclusively so Vert.x can't use the port number */
      occupied = new ServerSocket(0);
      occupied.setReuseAddress(false);
      server = vertx.createHttpServer(new HttpServerOptions().setPort(occupied.getLocalPort()));
      server.requestHandler(noOpHandler()).listen().onComplete(onFailure(server -> testComplete()));
      await();
    }finally {
      if( occupied != null ) {
        occupied.close();
      }
    }
  }

  @Test
  public void testListenInvalidHost() {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost("iqwjdoqiwjdoiqwdiojwd"));
    server.requestHandler(noOpHandler());
    server.listen().onComplete(onFailure(s -> testComplete()));
    await();
  }

  @Test
  public void testPauseResumeClientResponseWontCallEndHandlePrematurely() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(8192));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.bodyHandler(body -> {
          assertEquals(expected, body);
          testComplete();
        });
        // Check that pause resume won't call the end handler prematurely
        resp.pause();
        resp.resume();
      }));
    }));
    await();
  }

  @Test
  public void testPauseClientResponse() throws Exception {
    int numWrites = 10;
    int numBytes = 100;
    server.requestHandler(req -> {
      req.response().setChunked(true);
      // Send back a big response in several chunks
      for (int i = 0; i < numWrites; i++) {
        req.response().write(TestUtils.randomBuffer(numBytes));
      }
      req.response().end();
    });

    startServer(testAddress);

    AtomicBoolean paused = new AtomicBoolean();
    Buffer totBuff = Buffer.buffer();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.pause();
        paused.set(true);
        resp.handler(chunk -> {
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
      }));
    }));
    await();
  }

  @Test
  public void testDeliverPausedBufferWhenResume() throws Exception {
    testDeliverPausedBufferWhenResume(block -> vertx.setTimer(10, id -> block.run()));
  }

  @Test
  public void testDeliverPausedBufferWhenResumeOnOtherThread() throws Exception {
    ExecutorService exec = Executors.newSingleThreadExecutor();
    try {
      testDeliverPausedBufferWhenResume(block -> exec.execute(() -> {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          fail(e);
          Thread.currentThread().interrupt();
        }
        block.run();
      }));
    } finally {
      exec.shutdown();
    }
  }

  private void testDeliverPausedBufferWhenResume(Consumer<Runnable> scheduler) throws Exception {
    Buffer data = TestUtils.randomBuffer(2048);
    int num = 10;
    waitFor(num);
    List<CompletableFuture<Void>> resumes = Collections.synchronizedList(new ArrayList<>());
    for (int i = 0;i < num;i++) {
      resumes.add(new CompletableFuture<>());
    }
    server.requestHandler(req -> {
      int idx = Integer.parseInt(req.path().substring(1));
      HttpServerResponse resp = req.response();
      resumes.get(idx).thenAccept(v -> {
        resp.end();
      });
      resp.setChunked(true).write(data);
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < num;i++) {
      int idx = i;
      client.request(new RequestOptions(requestOptions).setURI("/" + i)).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          Buffer body = Buffer.buffer();
          Thread t = Thread.currentThread();
          resp.handler(buff -> {
            assertSame(t, Thread.currentThread());
            resumes.get(idx).complete(null);
            body.appendBuffer(buff);
          });
          resp.endHandler(v -> {
            // assertEquals(data, body);
            complete();
          });
          resp.pause();
          scheduler.accept(resp::resume);
        }));
      }));
    }
    await();
  }

  @Test
  public void testClearPausedBuffersWhenResponseEnds() throws Exception {
    Buffer data = TestUtils.randomBuffer(20);
    int num = 10;
    waitFor(num);
    server.requestHandler(req -> {
      req.response().end(data);
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setKeepAlive(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < num;i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals(data, buff);
            complete();
          });
          resp.pause();
          vertx.setTimer(10, id -> {
            resp.resume();
          });
        }));
      }));
    }
    await();
  }

  @Test
  public void testPausedHttpServerRequest() throws Exception {
    CompletableFuture<Void> resumeCF = new CompletableFuture<>();
    Buffer expected = Buffer.buffer();
    server.requestHandler(req -> {
      req.pause();
      AtomicBoolean paused = new AtomicBoolean(true);
      Buffer body = Buffer.buffer();
      req.handler(buff -> {
        assertFalse(paused.get());
        body.appendBuffer(buff);
      });
      resumeCF.thenAccept(v -> {
        paused.set(false);
        req.resume();
      });
      req.endHandler(v -> {
        assertEquals(expected, body);
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
        req
          .setChunked(true)
          .response().onComplete(onSuccess(resp -> {
            resp.endHandler(v -> {
              testComplete();
            });
          }));
        while (!req.writeQueueFull()) {
          Buffer buff = Buffer.buffer(TestUtils.randomAlphaString(1024));
          expected.appendBuffer(buff);
          req.write(buff);
        }
        resumeCF.complete(null);
        req.end();
    }));
    await();
  }

  @Test
  public void testHttpServerRequestPausedDuringLastChunk1() throws Exception {
    testHttpServerRequestPausedDuringLastChunk(false);
  }

  @Test
  public void testHttpServerRequestPausedDuringLastChunk2() throws Exception {
    testHttpServerRequestPausedDuringLastChunk(true);
  }

  private void testHttpServerRequestPausedDuringLastChunk(boolean fetching) throws Exception {
    server.requestHandler(req -> {
      AtomicBoolean ended = new AtomicBoolean();
      AtomicBoolean paused = new AtomicBoolean();
      req.handler(buff -> {
        assertEquals("small", buff.toString());
        req.pause();
        paused.set(true);
        vertx.setTimer(20, id -> {
          assertFalse(ended.get());
          paused.set(false);
          if (fetching) {
            req.fetch(1);
          } else {
            req.resume();
          }
        });
      });
      req.endHandler(v -> {
        assertFalse(paused.get());
        ended.set(true);
        req.response().end();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req
        .send(Buffer.buffer("small")).onComplete(onSuccess(resp -> {
          complete();
        }));
    }));
    await();
  }

  @Test
  public void testHttpClientResponsePausedDuringLastChunk1() throws Exception {
    testHttpClientResponsePausedDuringLastChunk(false);
  }

  @Test
  public void testHttpClientResponsePausedDuringLastChunk2() throws Exception {
    testHttpClientResponsePausedDuringLastChunk(true);
  }

  private void testHttpClientResponsePausedDuringLastChunk(boolean fetching) throws Exception {
    server.requestHandler(req -> {
      req.response().end("small");
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        AtomicBoolean ended = new AtomicBoolean();
        AtomicBoolean paused = new AtomicBoolean();
        resp.handler(buff -> {
          assertEquals("small", buff.toString());
          resp.pause();
          paused.set(true);
          vertx.setTimer(20, id -> {
            assertFalse(ended.get());
            paused.set(false);
            if (fetching) {
              resp.fetch(1);
            } else {
              resp.resume();
            }
          });
        });
        resp.endHandler(v -> {
          assertFalse(paused.get());
          ended.set(true);
          complete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testHostHeaderOverridePossible() throws Exception {
    server.requestHandler(req -> {
      assertEquals("localhost", req.authority().host());
      assertEquals(4444, req.authority().port());
      req.response().end();
    });

    startServer(testAddress);
    client.request(new RequestOptions().setServer(testAddress).setHost("localhost").setPort(4444))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> testComplete()));

    await();
  }

  @Test
  public void testResponseBodyWriteFixedString() throws Exception {
    String body = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
    Buffer bodyBuff = Buffer.buffer(body);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
      .send()
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body))
      .onComplete(onSuccess(buff -> {
        assertEquals(bodyBuff, buff);
        testComplete();
      }));

    await();
  }

  @Test
  public void testClientMultiThreaded() throws Exception {
    int numThreads = 10;
    Thread[] threads = new Thread[numThreads];
    CountDownLatch latch = new CountDownLatch(numThreads);
    server.requestHandler(req -> {
      req.response().putHeader("count", req.headers().get("count"));
      req.response().end();
    });
    startServer(testAddress);
    for (int i = 0; i < numThreads; i++) {
      int index = i;
      threads[i] = new Thread() {
        public void run() {
          client.request(requestOptions)
            .compose(req -> req.putHeader("count", String.valueOf(index)).send())
            .onComplete(onSuccess(resp -> {
              assertEquals(200, resp.statusCode());
              assertEquals(String.valueOf(index), resp.headers().get("count"));
              latch.countDown();
            }));
        }
      };
      threads[i].start();
    }
    awaitLatch(latch);
    for (int i = 0; i < numThreads; i++) {
      threads[i].join();
    }
  }

  @Test
  public void testInVerticle() throws Exception {
    testInVerticle(false);
  }

  private void testInVerticle(boolean worker) {
    client.close();
    server.close();
    class MyVerticle extends AbstractVerticle {
      Context ctx;
      @Override
      public void start() {
        ctx = Vertx.currentContext();
        if (worker) {
          assertTrue(ctx.isWorkerContext());
        } else {
          assertTrue(ctx.isEventLoopContext());
        }
        Thread thr = Thread.currentThread();
        server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
        server.requestHandler(req -> {
          req.response().end();
          assertSameEventLoop(ctx, Vertx.currentContext());
          assertSame(((HttpServerRequestInternal)req).context(), Vertx.currentContext());
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
        });
        server
          .listen(testAddress)
          .onComplete(onSuccess(s -> {
          assertSame(ctx, Vertx.currentContext());
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          client = vertx.createHttpClient(new HttpClientOptions());
          client
            .request(requestOptions)
            .compose(HttpClientRequest::send)
            .onComplete(onSuccess(resp -> {
              assertSameEventLoop(ctx, Vertx.currentContext());
              if (!worker) {
                assertSame(thr, Thread.currentThread());
              }
              assertEquals(200, resp.statusCode());
              testComplete();
          }));
        }));
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setThreadingModel(worker ? ThreadingModel.WORKER : ThreadingModel.EVENT_LOOP));
    await();
  }

  @Test
  public void testWorkerServer() throws Exception {
    int numReq = 5; // 5 == the HTTP/1 pool max size
    waitFor(numReq);
    AtomicBoolean owner = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger connCount = new AtomicInteger();
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        vertx.createHttpServer(createBaseServerOptions())
          .requestHandler(req -> {
            Context current = Vertx.currentContext();
            assertTrue(current.isWorkerContext());
            assertSameEventLoop(context, current);
            try {
              assertTrue(owner.compareAndSet(false, true));
              Thread.sleep(200);
            } catch (Exception e) {
              fail(e);
            } finally {
              owner.set(false);
            }
            req.response().end("pong");
          }).connectionHandler(conn -> {
          Context current = Vertx.currentContext();
          assertTrue(Context.isOnEventLoopThread());
          assertTrue(current.isEventLoopContext());
          assertNotSame(context, current);
          connCount.incrementAndGet(); // No complete here as we may have 1 or 5 connections depending on the protocol
        }).listen(testAddress)
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER))
      .onComplete(onSuccess(id -> latch.countDown()));
    awaitLatch(latch);
    for (int i = 0;i < numReq;i++) {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(
          onSuccess(resp -> {
            complete();
          }));
    }
    await();
    assertTrue(connCount.get() > 0);
  }

  @Test
  public void testInWorker() throws Exception {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        assertTrue(Vertx.currentContext().isWorkerContext());
        assertTrue(Context.isOnWorkerThread());
        HttpServer server = vertx.createHttpServer(createBaseServerOptions());
        server.requestHandler(req -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          Buffer buf = Buffer.buffer();
          req.handler(buf::appendBuffer);
          req.endHandler(v -> {
            assertEquals("hello", buf.toString());
            req.response().end("bye");
          });
        }).listen(testAddress).onComplete(onSuccess(s -> {
          assertTrue(Vertx.currentContext().isWorkerContext());
          assertTrue(Context.isOnWorkerThread());
          client.close();
          client = vertx.createHttpClient(createBaseClientOptions());
          client.request(new RequestOptions(requestOptions).setMethod(PUT)).onComplete(onSuccess(req -> {
            req.send(Buffer.buffer("hello"))
              .onComplete(onSuccess(resp -> {
                assertEquals(200, resp.statusCode());
                assertTrue(Vertx.currentContext().isWorkerContext());
                assertTrue(Context.isOnWorkerThread());
                resp.handler(buf -> {
                  assertEquals("bye", buf.toString());
                  resp.endHandler(v -> {
                    testComplete();
                  });
                });
              }));
          }));
        }));
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    await();
  }

  @Test
  public void testClientReadStreamInWorker() throws Exception {
    int numReq = 16;
    waitFor(numReq);
    Buffer body = Buffer.buffer(randomAlphaString(512 * 1024));
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer(createBaseServerOptions());
        server.requestHandler(req -> {
          req.response().end(body);
        }).listen(testAddress)
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
    })
      .await(20, TimeUnit.SECONDS);
    vertx.deployVerticle(new AbstractVerticle() {
      HttpClient client;
      @Override
      public void start(Promise<Void> startPromise) {
        client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
        for (int i = 0; i < numReq; i++) {
          client.request(requestOptions)
            .compose(req -> req
              .send()
              .compose(resp -> {
                resp.pause();
                vertx.setTimer(250, id -> {
                  resp.resume();
                });
                return resp.end();
              }))
            .onComplete(onSuccess(v -> complete()));
        }
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    await();
  }

  @Repeat(times = 16)
  @Test
  public void testServerReadStreamInWorker() throws Exception {
    int numReq = 1;
    waitFor(numReq);
    Buffer body = Buffer.buffer(randomAlphaString(512 * 1024));
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer(createBaseServerOptions());
        server.requestHandler(req -> {
          req.end().onComplete(onSuccess(v -> {
            req.response().end();
          }));
          req.pause();
          vertx.setTimer(10, id -> {
            req.resume();
          });
        }).listen(testAddress)
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER))
      .await(20, TimeUnit.SECONDS);
    vertx.deployVerticle(new AbstractVerticle() {
      HttpClient client;
      @Override
      public void start(Promise<Void> startPromise) {
        client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
        for (int i = 0; i < numReq; i++) {
          client.request(requestOptions).
            compose(req -> req
              .send(body)
              .compose(HttpClientResponse::end))
            .onComplete(onSuccess(v -> complete()));
        }
      }
    });
    await();
  }

  @Test
  public void testMultipleServerClose() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    // We assume the endHandler and the close completion handler are invoked in the same context task
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    server.close().onComplete(ar1 -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      server.close().onComplete(ar2 -> {
        server.close().onComplete(ar3 -> {
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testRequestEnded() throws Exception {
    server.requestHandler(req -> {
      assertFalse(req.isEnded());
      req.endHandler(v -> {
        assertTrue(req.isEnded());
        try  {
          req.endHandler(v2 -> {});
          fail("Shouldn't be able to set end handler");
        } catch (IllegalStateException e) {
          // OK
        }
        try  {
          req.setExpectMultipart(true);
          fail("Shouldn't be able to set expect multipart");
        } catch (IllegalStateException e) {
          // OK
        }
        try  {
          req.bodyHandler(v2 -> {
          });
          fail("Shouldn't be able to set body handler");
        } catch (IllegalStateException e) {
          // OK
        }
        try  {
          req.handler(v2 -> {
          });
          fail("Shouldn't be able to set handler");
        } catch (IllegalStateException e) {
          // OK
        }

        req.response().setStatusCode(200).end();
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }));
    await();
  }

  @Test
  public void testRequestEndedNoEndHandler() throws Exception {
    server.requestHandler(req -> {
      assertFalse(req.isEnded());
      req.response().setStatusCode(200).end();
      vertx.setTimer(500, v -> {
        assertTrue(req.isEnded());
        try {
          req.endHandler(v2 -> {
          });
          fail("Shouldn't be able to set end handler");
        } catch (IllegalStateException e) {
          // OK
        }
        try {
          req.setExpectMultipart(true);
          fail("Shouldn't be able to set expect multipart");
        } catch (IllegalStateException e) {
          // OK
        }
        try {
          req.bodyHandler(v2 -> {
          });
          fail("Shouldn't be able to set body handler");
        } catch (IllegalStateException e) {
          // OK
        }
        try {
          req.handler(v2 -> {
          });
          fail("Shouldn't be able to set handler");
        } catch (IllegalStateException e) {
          // OK
        }
        testComplete();
      });
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
      }));
    await();
  }

  @Test
  public void testAbsoluteURIServer() throws Exception {
    server.close();
    // Listen on all addresses
    server = vertx.createHttpServer(createBaseServerOptions().setHost("0.0.0.0"));
    server.requestHandler(req -> {
      String absURI = req.absoluteURI();
      assertEquals(req.scheme() + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/path", absURI);
      req.response().end();
    });
    startServer(testAddress);
    String path = "/path";
    client.request(new RequestOptions(requestOptions).setURI(path))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }));

    await();
  }

  @Test
  public void testDumpManyRequestsOnQueue() throws Exception {
    int sendRequests = 10000;
    AtomicInteger receivedRequests = new AtomicInteger();
    HttpClientOptions ops = createBaseClientOptions()
      .setDefaultPort(DEFAULT_HTTP_PORT)
      .setPipelining(true)
      .setKeepAlive(true);
    client.close();
    client = vertx.createHttpClient(ops);
    server.requestHandler(r-> {
      r.response().end();
      if (receivedRequests.incrementAndGet() == sendRequests) {
        testComplete();
      }
    });
    startServer(testAddress);
    IntStream.range(0, sendRequests)
      .forEach(x -> client.request(requestOptions).compose(HttpClientRequest::send));
    await();
  }

  @Test
  public void testOtherMethodRequest() throws Exception {
    server.requestHandler(r -> {
      assertEquals("COPY", r.method().name());
      r.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.valueOf("COPY")))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> testComplete()));
    await();
  }

  @Test
  public void testClientGlobalConnectionHandler() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        assertSame(ctx.nettyEventLoop(), ((ContextInternal)Vertx.currentContext()).nettyEventLoop());
        complete();
      })
      .build();
    ctx.runOnContext(v -> {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(resp -> complete());
    });
    await();
  }

  @Test
  public void testServerConnectionHandler() throws Exception {
    AtomicInteger status = new AtomicInteger();
    AtomicReference<HttpConnection> connRef = new AtomicReference<>();
    Context serverCtx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      assertSameEventLoop(serverCtx, Vertx.currentContext());
      assertEquals(0, status.getAndIncrement());
      assertNull(connRef.getAndSet(conn));
    });
    server.requestHandler(req -> {
      assertEquals(1, status.getAndIncrement());
      assertSame(connRef.get(), req.connection());
      req.response().end();
    });
    startServer(testAddress, serverCtx, server);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(resp -> testComplete());
    await();
  }

  @Test
  public void testServerConnectionHandlerClose() throws Exception {
    waitFor(2);
    Context serverCtx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      conn.close();
      conn.closeHandler(v -> complete());
    });
    server.requestHandler(req -> {
    });
    startServer(testAddress, serverCtx, server);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          complete();
        });
      })
      .build();
    client.request(requestOptions).compose(HttpClientRequest::send);
    await();
  }

  @Test
  public void testClientConnectionClose() throws Exception {
    // Test client connection close + server close handler
    Promise<Void> latch = Promise.promise();
    server.requestHandler(req -> {
      AtomicInteger len = new AtomicInteger();
      req.handler(buff -> {
        if (len.addAndGet(buff.length()) == 1024) {
          latch.complete();
        }
      });
      req.connection().closeHandler(v -> {
        testComplete();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(onSuccess(req -> {
      req
        .setChunked(true)
        .write(TestUtils.randomBuffer(1024));
      latch.future().onComplete(onSuccess(v -> {
        req.connection().close();
      }));
    }));
    await();
  }

  @Test
  public void testServerConnectionClose() throws Exception {
    // Test server connection close + client close handler
    waitFor(2);
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(conn -> {
        conn.closeHandler(v -> {
          complete();
        });
      })
      .build();
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onFailure(req -> complete()));
    await();
  }

  @Test
  public void testNoLogging() throws Exception {
    TestLoggerFactory factory = testLogging();
    assertFalse(factory.hasName("io.netty.handler.codec.http2.Http2FrameLogger"));
  }

  @Test
  public void testServerLogging() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setLogActivity(true));
    TestLoggerFactory factory = testLogging();
    if (this instanceof Http1xTest) {
      assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
    } else {
      assertTrue(factory.hasName("io.netty.handler.codec.http2.Http2FrameLogger"));
    }
  }

  @Test
  public void testClientLogging() throws Exception {
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setLogActivity(true));
    TestLoggerFactory factory = testLogging();
    if (this instanceof Http1xTest) {
      assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
    } else {
      assertTrue(factory.hasName("io.netty.handler.codec.http2.Http2FrameLogger"));
    }
  }

  @Test
  public void testClientLocalAddress() throws Exception {
    String expectedAddress = TestUtils.loopbackAddress();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setLocalAddress(expectedAddress));
    server.requestHandler(req -> {
      assertEquals(expectedAddress, req.remoteAddress().host());
      req.response().end();
    });
    startServer();
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }));
    await();
  }

  @Test
  public void testFollowRedirectGetOn301() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 301, 200, 2, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected");
  }

  @Test
  public void testFollowRedirectPostOn301() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.GET, 301, 301, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectPutOn301() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.GET, 301, 301, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectGetOn302() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 302, 200, 2, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected");
  }

  @Test
  public void testFollowRedirectPostOn302() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.GET, 302, 302, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectPutOn302() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.GET, 302, 302, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectGetOn303() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 303, 200, 2, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected");
  }

  @Test
  public void testFollowRedirectPostOn303() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.GET, 303, 200, 2, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected");
  }

  @Test
  public void testFollowRedirectPutOn303() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.GET, 303, 200, 2, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected");
  }

  @Test
  public void testFollowRedirectNotOn304() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 304, 304, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectGetOn307() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 307, 200, 2, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected");
  }

  @Test
  public void testFollowRedirectPostOn307() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.POST, 307, 307, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectPutOn307() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.PUT, 307, 307, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectWithRelativeLocation() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 301, 200, 2, "/another", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/another");
  }

  @Test
  public void testFollowRedirectGetOn308() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 308, 200, 2, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected");
  }

  @Test
  public void testFollowRedirectPostOn308() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.POST, 308, 308, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }

  @Test
  public void testFollowRedirectPutOn308() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.PUT, 308, 308, 1, "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/redirected", "http://" + DEFAULT_HTTP_HOST_AND_PORT + "/somepath");
  }


  @Test
  public void testFollowRedirectsWithProxy() throws Exception {
    Assume.assumeThat("Proxy is only supported with HTTP/1", this, instanceOf(Http1xTest.class));
    waitFor(2);
    String location = "http://" + DEFAULT_HTTP_HOST + ":" + DEFAULT_HTTP_PORT + "/ok";
    server.requestHandler(req -> {
      if (!req.headers().contains("foo", "bar", true)) {
        fail("Missing expected header");
        return;
      }
      assertEquals(Collections.singletonList("bar"), req.headers().getAll("foo"));
      if (req.path().equals("/redirect")) {
        req.response().setStatusCode(301).putHeader("Location", location).end();
      } else {
        req.response().end(req.path());
        complete();
      }
    });

    startServer();
    startProxy(null, ProxyType.HTTP);
    client.request(
        new RequestOptions(requestOptions)
          .setServer(null)
          .setMethod(GET)
          .setURI("/redirect")
          .setProxyOptions(new ProxyOptions().setPort(proxy.port()))
      )
      .compose(req -> req
        .putHeader("foo", "bar")
        .setFollowRedirects(true)
        .send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals(location, proxy.getLastUri());
          return resp.body().compose(body -> {
            if (resp.statusCode() == 200) {
              assertEquals(Buffer.buffer("/ok"), body);
            } else {
              assertEquals(Buffer.buffer(), body);
            }
            return Future.succeededFuture();
          });
        })
      ).onSuccess(v -> testComplete());
    await();
  }

  private void testFollowRedirect(
      HttpMethod method,
      HttpMethod expectedMethod,
      int statusCode,
      int expectedStatus,
      int expectedRequests,
      String location,
      String expectedURI) throws Exception {
    String s;
    if (createBaseServerOptions().isSsl() && location.startsWith("http://")) {
      s = "https://" + location.substring("http://".length());
    } else {
      s = location;
    }
    String t;
    if (createBaseServerOptions().isSsl() && expectedURI.startsWith("http://")) {
      t = "https://" + expectedURI.substring("http://".length());
    } else {
      t = expectedURI;
    }
    AtomicInteger numRequests = new AtomicInteger();
    Buffer expectedBody = Buffer.buffer(TestUtils.randomAlphaString(256));
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      if (numRequests.getAndIncrement() == 0) {
        resp.setStatusCode(statusCode);
        if (s != null) {
          resp.putHeader(HttpHeaders.LOCATION, s);
        }
        resp.end();
      } else {
        assertEquals(t, req.absoluteURI());
        assertEquals("foo_value", req.getHeader("foo"));
        assertEquals(expectedMethod, req.method());
        resp.end(expectedBody);
      }
    });
    startServer();
    client.request(
      new RequestOptions(requestOptions)
        .setServer(null)
        .setMethod(method)
        .setURI("/somepath")
    )
      .compose(req -> req
        .putHeader("foo", "foo_value")
        .setFollowRedirects(true)
        .send()
        .compose(resp -> {
          assertEquals(resp.request().absoluteURI(), t);
          assertEquals(expectedRequests, numRequests.get());
          assertEquals(expectedStatus, resp.statusCode());
          return resp.body().compose(body -> {
            if (resp.statusCode() == 200) {
              assertEquals(expectedBody, body);
            } else {
              assertEquals(Buffer.buffer(), body);
            }
            return Future.succeededFuture();
          });
        })
      ).onSuccess(v -> testComplete());
    await();
  }

  @Test
  public void testFollowRedirectWithBody() throws Exception {
    testFollowRedirectWithBody(Function.identity());
  }

  @Test
  public void testFollowRedirectWithPaddedBody() throws Exception {
    testFollowRedirectWithBody(buff -> TestUtils.leftPad(1, buff));
  }

  private void testFollowRedirectWithBody(Function<Buffer, Buffer> translator) throws Exception {
    Buffer expected = TestUtils.randomBuffer(2048);
    AtomicBoolean redirected = new AtomicBoolean();
    server.requestHandler(req -> {
      if (redirected.compareAndSet(false, true)) {
        assertEquals(HttpMethod.PUT, req.method());
        req.bodyHandler(body -> {
          assertEquals(body, expected);
          String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
          req.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, scheme + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/whatever").end();
        });
      } else {
        assertEquals(HttpMethod.GET, req.method());
        assertNull(req.getHeader(HttpHeaders.CONTENT_LENGTH));
        req.response().end();
      }
    });
    startServer();
    client.request(new RequestOptions()
      .setMethod(HttpMethod.PUT)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
    )
      .onComplete(onSuccess(req -> {
        req.setFollowRedirects(true);
        req.send(translator.apply(expected)).onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testFollowRedirectHappensAfterResponseIsReceived() throws Exception {
    AtomicBoolean redirected = new AtomicBoolean();
    AtomicBoolean sent = new AtomicBoolean();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      if (redirected.compareAndSet(false, true)) {
        String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
        resp
          .setStatusCode(303)
          .putHeader(HttpHeaders.CONTENT_LENGTH, "11")
          .putHeader(HttpHeaders.LOCATION, scheme + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/whatever")
          .write("hello ");
        vertx.setTimer(500, id -> {
          sent.set(true);
          resp.end("world");
        });
      } else {
        assertTrue(sent.get());
        resp.end();
      }
    });
    startServer();
    client.request(new RequestOptions()
      .setMethod(HttpMethod.PUT)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
    )
      .onComplete(onSuccess(req -> {
        req.setFollowRedirects(true);
        req.send().onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }));
      }));
    await();
  }

  @Test
  public void testFollowRedirectWithChunkedBody() throws Exception {
    Buffer buff1 = Buffer.buffer(TestUtils.randomAlphaString(2048));
    Buffer buff2 = Buffer.buffer(TestUtils.randomAlphaString(2048));
    Buffer expected = Buffer.buffer().appendBuffer(buff1).appendBuffer(buff2);
    AtomicBoolean redirected = new AtomicBoolean();
    Promise<Void> latch = Promise.promise();
    server.requestHandler(req -> {
      boolean redirect = redirected.compareAndSet(false, true);
      if (redirect) {
        latch.complete();
      }
      if (redirect) {
        assertEquals(HttpMethod.PUT, req.method());
        req.bodyHandler(body -> {
          assertEquals(body, expected);
          String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
          req.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, scheme + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/whatever").end();
        });
      } else {
        assertEquals(HttpMethod.GET, req.method());
        req.response().end();
      }
    });
    startServer();
    client.request(new RequestOptions()
      .setMethod(HttpMethod.PUT)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setURI(DEFAULT_TEST_URI)
    )
      .onComplete(onSuccess(req -> {
        req.setFollowRedirects(true);
        req.setChunked(true);
        req.write(buff1);
        latch.future().onSuccess(v -> {
          req.end(buff2);
        });
        req.response().onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }));
      }));
    await();
  }

  @Test
  public void testFollowRedirectWithRequestNotEnded() throws Exception {
    testFollowRedirectWithRequestNotEnded(false);
  }

  @Test
  public void testFollowRedirectWithRequestNotEndedFailing() throws Exception {
    testFollowRedirectWithRequestNotEnded(true);
  }

  private void testFollowRedirectWithRequestNotEnded(boolean expectFail) throws Exception {
    Buffer buff1 = Buffer.buffer(TestUtils.randomAlphaString(2048));
    Buffer buff2 = Buffer.buffer(TestUtils.randomAlphaString(2048));
    Buffer expected = Buffer.buffer().appendBuffer(buff1).appendBuffer(buff2);
    AtomicBoolean redirected = new AtomicBoolean();
    Promise<Void> latch = Promise.promise();
    server.requestHandler(req -> {
      boolean redirect = redirected.compareAndSet(false, true);
      if (redirect) {
        Buffer body = Buffer.buffer();
        req.handler(buff -> {
          if (body.length() == 0) {
            HttpServerResponse resp = req.response();
            String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
            resp.setStatusCode(303).putHeader(HttpHeaders.LOCATION, scheme + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/whatever");
            if (expectFail) {
              resp.setChunked(true).write("whatever");
              vertx.runOnContext(v -> {
                req.connection().close();
              });
            } else {
              resp.end();
            }
            latch.complete();
          }
          body.appendBuffer(buff);
        });
        req.endHandler(v -> {
          assertEquals(expected, body);
        });
      } else {
        req.response().end();
      }
    });
    startServer();
    AtomicBoolean called = new AtomicBoolean();
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(DEFAULT_TEST_URI)
      .setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
        req.response().onComplete(ar -> {
          assertEquals(expectFail, ar.failed());
          if (ar.succeeded()) {
            HttpClientResponse resp = ar.result();
            assertEquals(200, resp.statusCode());
            testComplete();
          } else {
            if (called.compareAndSet(false, true)) {
              testComplete();
            }
          }
        });
        latch.future().onComplete(onSuccess(v -> {
          // Wait so we end the request while having received the server response (but we can't be notified)
          if (!expectFail) {
            vertx.setTimer(500, id -> {
              req.end(buff2);
            });
          }
        }));
        req
          .setFollowRedirects(true)
          .setChunked(true)
          .write(buff1);
    }));
    await();
  }

  @Test
  public void testFollowRedirectSendHeadThenBody() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(2048));
    AtomicBoolean redirected = new AtomicBoolean();
    server.requestHandler(req -> {
      if (redirected.compareAndSet(false, true)) {
        assertEquals(HttpMethod.PUT, req.method());
        req.bodyHandler(body -> {
          assertEquals(body, expected);
          req.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/whatever").end();
        });
      } else {
        assertEquals(HttpMethod.GET, req.method());
        req.response().end();
      }
    });
    startServer();
    client.request(new RequestOptions()
      .setMethod(HttpMethod.PUT)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setURI("/somepath")
    ).onComplete(onSuccess(req -> {
      req
        .setFollowRedirects(true)
        .putHeader("Content-Length", "" + expected.length())
        .response().onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }));
      req.sendHead().onComplete(onSuccess(v -> {
        req.end(expected);
      }));
    }));
    await();
  }

  @Test
  public void testFollowRedirectLimit() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    AtomicInteger numberOfRequests = new AtomicInteger();
    server.requestHandler(req -> {
      int val = numberOfRequests.incrementAndGet();
      if (val > 17) {
        fail();
      } else {
        String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
        req.response().setStatusCode(301).putHeader(HttpHeaders.LOCATION, scheme + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/otherpath").end();
      }
    });
    startServer();
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.setFollowRedirects(true);
        req.send().onComplete(onSuccess(resp -> {
          assertEquals(17, numberOfRequests.get());
          assertEquals(301, resp.statusCode());
          assertEquals("/otherpath", resp.request().path());
          testComplete();
        }));
      }));
    await();
  }

  @Test
  public void testFollowRedirectPropagatesTimeout() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    AtomicInteger redirections = new AtomicInteger();
    server.requestHandler(req -> {
      switch (redirections.getAndIncrement()) {
        case 0:
          String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
          req.response().setStatusCode(307).putHeader(HttpHeaders.LOCATION, scheme + "://" + DEFAULT_HTTP_HOST_AND_PORT + "/whatever").end();
          break;
      }
    });
    startServer();
    AtomicBoolean done = new AtomicBoolean();
    client.request(new RequestOptions(requestOptions)
      .setIdleTimeout(500)).onComplete(onSuccess(req -> {
      req
        .setFollowRedirects(true)
        .send()
        .onComplete(onFailure(t -> {
        if (done.compareAndSet(false, true)) {
          assertEquals(2, redirections.get());
          testComplete();
        }
      }));
    }));
    await();
  }

  @Test
  public void testFollowRedirectHost() throws Exception {
    String scheme = createBaseClientOptions().isSsl() ? "https" : "http";
    waitFor(2);
    HttpServerOptions options = createBaseServerOptions();
    int port = options.getPort() + 1;
    options.setPort(port);
    AtomicInteger redirects = new AtomicInteger();
    server.requestHandler(req -> {
      redirects.incrementAndGet();
      req.response().setStatusCode(301).putHeader(HttpHeaders.LOCATION, scheme + "://localhost:" + port + "/whatever").end();
    });
    startServer(testAddress);
    HttpServer server2 = vertx.createHttpServer(options);
    server2.requestHandler(req -> {
      assertEquals(1, redirects.get());
      assertEquals(scheme + "://localhost:" + port + "/whatever", req.absoluteURI());
      req.response().end();
      complete();
    });
    startServer(server2);
    client.request(requestOptions)
      .compose(req -> req
        .setFollowRedirects(true)
        // .setAuthority("localhost:" + options.getPort())
        .send()
      )
      .onComplete(onSuccess(resp -> {
        assertEquals(scheme + "://localhost:" + port + "/whatever", resp.request().absoluteURI());
        complete();
      }));
    await();
  }

  @Test
  public void testFollowRedirectWithCustomHandler() throws Exception {
    String scheme = createBaseClientOptions().isSsl() ? "https" : "http";
    waitFor(2);
    HttpServerOptions options = createBaseServerOptions();
    int port = options.getPort() + 1;
    options.setPort(port);
    AtomicInteger redirects = new AtomicInteger();
    server.requestHandler(req -> {
      redirects.incrementAndGet();
      req.response().setStatusCode(301).putHeader(HttpHeaders.LOCATION, scheme + "://localhost:" + port + "/whatever").end();
    });
    startServer();
    HttpServer server2 = vertx.createHttpServer(options);
    server2.requestHandler(req -> {
      assertEquals(1, redirects.get());
      assertEquals(scheme + "://localhost:" + port + "/custom", req.absoluteURI());
      req.response().end();
      complete();
    });
    startServer(server2);
    Context ctx = vertx.getOrCreateContext();
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withRedirectHandler(resp -> {
        assertEquals(ctx, Vertx.currentContext());
        Promise<RequestOptions> fut = Promise.promise();
        vertx.setTimer(25, id -> {
          fut.complete(new RequestOptions().setAbsoluteURI(scheme + "://localhost:" + port + "/custom"));
        });
        return fut.future();
      })
      .build();
    ctx.runOnContext(v -> {
      client.request(new RequestOptions()
        .setHost(DEFAULT_HTTP_HOST)
        .setPort(DEFAULT_HTTP_PORT)).onComplete(onSuccess(req -> {
          req.setFollowRedirects(true);
          req.putHeader("foo", "foo_value");
          req
            .send()
            .onComplete(onSuccess(resp -> {
            assertEquals(scheme + "://localhost:" + port + "/custom", resp.request().absoluteURI());
            complete();
          }));
      }));
    });
    await();
  }

  @Test
  public void testDefaultRedirectHandler() throws Exception {
    testFoo("http://example.com", "http://example.com");
    testFoo("http://example.com/somepath", "http://example.com/somepath");
    testFoo("http://example.com:8000", "http://example.com:8000");
    testFoo("http://example.com:8000/somepath", "http://example.com:8000/somepath");
    testFoo("https://example.com", "https://example.com");
    testFoo("https://example.com/somepath", "https://example.com/somepath");
    testFoo("https://example.com:8000", "https://example.com:8000");
    testFoo("https://example.com:8000/somepath", "https://example.com:8000/somepath");
    testFoo("whatever://example.com", null);
    testFoo("http://", null);
    testFoo("http://:" + DEFAULT_HTTP_PORT+ "/somepath", null);
  }

  private void testFoo(String location, String expectedAbsoluteURI) throws Exception {
    int status = 301;
    MultiMap headers = HttpHeaders.headers().add(HttpHeaders.LOCATION.toString(), location);
    HttpMethod method = HttpMethod.GET;
    String baseURI = "https://" + DEFAULT_HTTP_HOST_AND_PORT;
    class MockReq implements HttpClientRequest {
      public HttpClientRequest exceptionHandler(Handler<Throwable> handler) { throw new UnsupportedOperationException(); }
      public Future<Void> write(Buffer data) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setWriteQueueMaxSize(int maxSize) { throw new UnsupportedOperationException(); }
      public HttpClientRequest drainHandler(Handler<Void> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setFollowRedirects(boolean followRedirects) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setMaxRedirects(int maxRedirects) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setChunked(boolean chunked) { throw new UnsupportedOperationException(); }
      public boolean isChunked() { return false; }
      public HttpClientRequest authority(HostAndPort authority) { throw new UnsupportedOperationException(); }
      public HttpMethod getMethod() { return method; }
      public String absoluteURI() { return baseURI; }
      public HttpVersion version() { return HttpVersion.HTTP_1_1; }
      public String getURI() { throw new UnsupportedOperationException(); }
      public HttpClientRequest setURI(String uri) { throw new UnsupportedOperationException(); }
      public String path() { throw new UnsupportedOperationException(); }
      public String query() { throw new UnsupportedOperationException(); }
      public MultiMap headers() { return headers; }
      public HttpClientRequest putHeader(String name, String value) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(CharSequence name, CharSequence value) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(String name, Iterable<String> values) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) { throw new UnsupportedOperationException(); }
      public Future<Void> write(String chunk) { throw new UnsupportedOperationException(); }
      public Future<Void> write(String chunk, String enc) { throw new UnsupportedOperationException(); }
      public HttpClientRequest traceOperation(String op) { throw new UnsupportedOperationException(); }
      public String traceOperation() { throw new UnsupportedOperationException(); }
      public HttpClientRequest continueHandler(@Nullable Handler<Void> handler) { throw new UnsupportedOperationException(); }
      public boolean isFollowRedirects() { throw new UnsupportedOperationException(); }
      public int getMaxRedirects() { throw new UnsupportedOperationException(); }
      public int numberOfRedirections() { throw new UnsupportedOperationException(); }
      public HttpClientRequest redirectHandler(@Nullable Function<HttpClientResponse, Future<HttpClientRequest>> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest earlyHintsHandler(@Nullable Handler<MultiMap> handler) { throw new UnsupportedOperationException(); }
      public Future<Void> sendHead() { throw new UnsupportedOperationException(); }
      public Future<HttpClientResponse> connect() { throw new UnsupportedOperationException(); }
      public Future<Void> end(String chunk) { throw new UnsupportedOperationException(); }
      public Future<Void> end(String chunk, String enc) { throw new UnsupportedOperationException(); }
      public Future<Void> end() { throw new UnsupportedOperationException(); }
      public Future<Void> end(Buffer chunk) { throw new UnsupportedOperationException(); }
      public HttpClientRequest idleTimeout(long timeoutMs) { throw new UnsupportedOperationException(); }
      public HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) { throw new UnsupportedOperationException(); }
      public boolean reset(long code) { return false; }
      public boolean reset(long code, Throwable cause) { return false; }
      public HttpClientConnection connection() { throw new UnsupportedOperationException(); }
      public Future<Void> writeCustomFrame(int type, int flags, Buffer payload) { throw new UnsupportedOperationException(); }
      public boolean writeQueueFull() { throw new UnsupportedOperationException(); }
      public StreamPriorityBase getStreamPriority() { return null; }
      public HttpClientRequest setMethod(HttpMethod method) { throw new UnsupportedOperationException(); }
      public Future<HttpClientResponse> response() { throw new UnsupportedOperationException(); }
    }
    HttpClientRequest req = new MockReq();
    class MockResp implements HttpClientResponse {
      public HttpClientResponse resume() { throw new UnsupportedOperationException(); }
      public HttpClientResponse exceptionHandler(Handler<Throwable> handler) { throw new UnsupportedOperationException(); }
      public HttpClientResponse handler(Handler<Buffer> handler) { throw new UnsupportedOperationException(); }
      public HttpClientResponse pause() { throw new UnsupportedOperationException(); }
      public HttpClientResponse fetch(long amount) { throw new UnsupportedOperationException(); }
      public HttpClientResponse endHandler(Handler<Void> endHandler) { throw new UnsupportedOperationException(); }
      public HttpVersion version() { throw new UnsupportedOperationException(); }
      public int statusCode() { return status; }
      public String statusMessage() { throw new UnsupportedOperationException(); }
      public MultiMap headers() { throw new UnsupportedOperationException(); }
      public String getHeader(String headerName) { return headers.get(headerName); }
      public String getHeader(CharSequence headerName) { return getHeader(headerName.toString()); }
      public String getTrailer(String trailerName) { throw new UnsupportedOperationException(); }
      public MultiMap trailers() { throw new UnsupportedOperationException(); }
      public List<String> cookies() { throw new UnsupportedOperationException(); }
      public HttpClientResponse customFrameHandler(Handler<HttpFrame> handler) { throw new UnsupportedOperationException(); }
      public NetSocket netSocket() { throw new UnsupportedOperationException(); }
      public HttpClientRequest request() { return req; }
      public HttpClientResponse streamPriorityHandler(Handler<StreamPriorityBase> handler) { return this; }
      public Future<Buffer> body() { throw new UnsupportedOperationException(); }
      public Future<Void> end() { throw new UnsupportedOperationException(); }
    }
    MockResp resp = new MockResp();
    Function<HttpClientResponse, Future<RequestOptions>> handler = ((HttpClientImpl)((CleanableHttpClient)client).delegate).redirectHandler();
    Future<RequestOptions> redirection = handler.apply(resp);
    if (expectedAbsoluteURI != null) {
      RequestOptions expectedOptions = new RequestOptions().setAbsoluteURI(location);
      assertEquals(expectedOptions.getHost(), redirection.result().getHost());
      assertEquals(expectedOptions.getPort(), redirection.result().getPort());
      assertEquals(expectedOptions.getURI(), redirection.result().getURI());
      assertEquals(expectedOptions.isSsl(), redirection.result().isSsl());
    } else {
      assertTrue(redirection == null || redirection.failed() || redirection.result().getHost() == null);
    }
  }

  @Test
  public void testFollowRedirectEncodedParams() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    String value1 = "\ud55c\uae00", value2 = "A B+C", value3 = "123 \u20ac";
    server.requestHandler(req -> {
      switch (req.path()) {
        case "/first/call/from/client":
          StringBuilder location = null;
          try {
            location = new StringBuilder()
              .append(req.scheme()).append("://").append(DEFAULT_HTTP_HOST).append(':').append(DEFAULT_HTTP_PORT)
              .append("/redirected/from/client?")
              .append("encoded1=").append(URLEncoder.encode(value1, "UTF-8")).append('&')
              .append("encoded2=").append(URLEncoder.encode(value2, "UTF-8")).append('&')
              .append("encoded3=").append(URLEncoder.encode(value3, "UTF-8"));
          } catch (UnsupportedEncodingException e) {
            fail(e);
          }
          req.response()
            .setStatusCode(302)
            .putHeader("location", location.toString())
            .send();
          break;
        case "/redirected/from/client":
          assertEquals(value1, req.params().get("encoded1"));
          assertEquals(value2, req.params().get("encoded2"));
          assertEquals(value3, req.params().get("encoded3"));
          req.response().end();
          break;
        default:
          fail("Unknown path: " + req.path());
      }
    });
    startServer();

    client.request(new RequestOptions(requestOptions)
      .setURI("/first/call/from/client")).onComplete(onSuccess(req -> {
        req.setFollowRedirects(true);
        req
          .send()
          .onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testEventHandlersNotHoldingLock() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      switch (req.path()) {
        case "/0":
          req.handler(chunk -> {
            assertFalse(Thread.holdsLock(conn));
          });
          req.endHandler(v -> {
            assertFalse(Thread.holdsLock(conn));
            req.response().end(TestUtils.randomAlphaString(256));
          });
          break;
        case "/1":
          AtomicBoolean paused = new AtomicBoolean();
          req.pause();
          paused.set(true);
          vertx.runOnContext(v -> {
            paused.set(false);
            req.resume();
          });
          req.handler(chunk -> {
            assertFalse(Thread.holdsLock(conn));
            assertFalse(paused.get());
            paused.set(true);
            req.pause();
            vertx.runOnContext(v -> {
              paused.set(false);
              req.resume();
            });
          });
          req.endHandler(v -> {
            assertFalse(Thread.holdsLock(conn));
            assertFalse(paused.get());
            req.response().end(TestUtils.randomAlphaString(256));
          });
          break;
      }
    });
    startServer(testAddress);
    for (int i = 0;i < 2;i++) {
      Buffer body = Buffer.buffer(randomAlphaString(256));
      client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST).setURI("/" + i))
        .onComplete(onSuccess(req -> req.send(body).onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          HttpConnection conn = resp.request().connection();
          switch (resp.request().path()) {
            case "/0":
              resp.handler(chunk -> {
                assertFalse(Thread.holdsLock(conn));
              });
              resp.endHandler(v -> {
                assertFalse(Thread.holdsLock(conn));
                complete();
              });
              break;
            case "/1":
              AtomicBoolean paused = new AtomicBoolean();
              resp.pause();
              paused.set(true);
              vertx.runOnContext(v -> {
                paused.set(false);
                resp.resume();
              });
              resp.handler(chunk -> {
                assertFalse(Thread.holdsLock(conn));
                assertFalse(paused.get());
                paused.set(true);
                resp.pause();
                vertx.runOnContext(v -> {
                  paused.set(false);
                  resp.resume();
                });
              });
              resp.endHandler(v -> {
                assertFalse(Thread.holdsLock(conn));
                assertFalse(paused.get());
                complete();
              });
              break;
          }
        }))));
    }
    await();
  }

  @Test
  public void testEventHandlersNotHoldingLockOnClose() throws Exception {
    waitFor(7);
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      req.exceptionHandler(atMostOnce(err -> {
        assertFalse(Thread.holdsLock(conn));
        complete();
      }));
      HttpServerResponse resp = req.response();
      resp.exceptionHandler(atMostOnce(err -> {
        assertFalse(Thread.holdsLock(conn));
        complete();
      }));
      resp.closeHandler(v -> {
        assertFalse(Thread.holdsLock(conn));
        complete();
      });
      conn.closeHandler(err -> {
        assertFalse(Thread.holdsLock(conn));
        complete();
      });
      resp.setChunked(true).write("hello");
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          HttpConnection conn = resp.request().connection();
          resp.exceptionHandler(atMostOnce(err -> {
            assertFalse(Thread.holdsLock(conn));
            complete();
          }));
          conn.closeHandler(v -> {
            assertFalse(Thread.holdsLock(conn));
            complete();
          });
          conn.close();
        }));
        req.exceptionHandler(atMostOnce(err -> {
          assertFalse(Thread.holdsLock(req.connection()));
          complete();
        }));
        req.setChunked(true).sendHead();
      }));
    await();
  }

  @Test
  public void testCloseHandlerWhenConnectionEnds() throws Exception {
    server.requestHandler(req -> {
      req.response().closeHandler(v -> {
        testComplete();
      });
      req.response().setChunked(true).write("some-data");
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          resp.handler(v -> {
            resp.request().connection().close();
          });
        }));
      }));
    await();
  }

  @Test
  public void testUseResponseAfterClose() throws Exception {
    testAfterServerResponseClose(resp -> {
      Buffer buff = Buffer.buffer();
      resp.drainHandler(noOpHandler());
      resp.exceptionHandler(noOpHandler());
      resp.setChunked(false);
      resp.setWriteQueueMaxSize(123);
      resp.writeQueueFull();
      resp.putHeader("foo", "bar");
      resp.setChunked(true);
      resp.write(buff);
      resp.write("foo");
      resp.write("foo", "UTF-8");
      resp.write(buff).onComplete(onFailure(err1 -> {
        resp.write("foo").onComplete(onFailure( err2 -> {
          resp.write("foo", "UTF-8").onComplete(onFailure(err3 -> {
            resp.end().onComplete(onFailure(err4 -> {
              testComplete();
            }));
          }));
        }));
      }));
    });
  }

  @Test
  public void testSendFileAfterServerResponseClose() throws Exception {
    testAfterServerResponseClose(resp -> {
      resp.sendFile("webroot/somefile.html");
      testComplete();
    });
  }

  @Test
  public void testSendFileAsyncAfterServerResponseClose() throws Exception {
    testAfterServerResponseClose(resp -> {
      resp.sendFile("webroot/somefile.html").onComplete(onFailure(err -> {
        testComplete();
      }));
    });
  }

  private void testAfterServerResponseClose(Handler<HttpServerResponse> test) throws Exception {
    AtomicReference<HttpConnection> clientConn = new AtomicReference<>();
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.closeHandler(v1 -> {
        test.handle(resp);
      });
      clientConn.get().close();
    });
    startServer(testAddress);
    client.close();
    client = vertx.httpClientBuilder()
      .with(createBaseClientOptions())
      .withConnectHandler(clientConn::set)
      .build();
    client.request(requestOptions).compose(HttpClientRequest::send).onComplete(onFailure(err -> {
    }));
    await();
  }

  @Test
  public abstract void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd() throws Exception;

  protected void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(int expected) throws Exception {
    AtomicInteger closeCount = new AtomicInteger();
    AtomicInteger endCount = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().closeHandler(v -> {
        closeCount.incrementAndGet();
      });
      req.response().endHandler(v -> {
        endCount.incrementAndGet();
      });
      req.connection().closeHandler(v -> {
        assertEquals(expected, closeCount.get());
        assertEquals(1, endCount.get());
        testComplete();
      });
      req.response().end("some-data");
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.endHandler(v -> {
          resp.request().connection().close();
        });
      }));
    }));
    await();
  }

  private TestLoggerFactory testLogging() throws Exception {
    return TestUtils.testLogging(() -> {
       try {
        server.requestHandler(req -> {
          req.response().end();
        });
        startServer(testAddress);
        client.request(requestOptions).onComplete(onSuccess(req -> {
          req.send().onComplete(onSuccess(resp -> {
            testComplete();
          }));
        }));
        await();
       } catch (Exception e) {
         throw new RuntimeException(e);
       }
    });
  }

  @Test
  public void testClientDecompressionError() throws Exception {
    server.requestHandler(req -> {
      req.response()
        .putHeader("Content-Encoding", "gzip")
        .end("long response with mismatched encoding causes connection leaks");
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setDecompressionSupported(true));
    client.request(requestOptions)
      .compose(req -> req.send().compose(HttpClientResponse::body))
      .onFailure(err -> {
      if (err instanceof Http2Exception) {
        testComplete();
      } else if (err instanceof DecompressionException) {
        testComplete();
      } else {
        fail();
      }
    });
    await();
  }

  @Test
  public void testContainsValueString() throws Exception {
    server.requestHandler(req -> {
      assertTrue(req.headers().contains("Foo", "foo", false));
      assertFalse(req.headers().contains("Foo", "fOo", false));
      req.response().putHeader("quux", "quux");
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.putHeader("foo", "foo").send())
      .onComplete(onSuccess(resp -> {
        assertTrue(resp.headers().contains("Quux", "quux", false));
        assertFalse(resp.headers().contains("Quux", "quUx", false));
        testComplete();
      }));
    await();
  }

  @Test
  public void testContainsValueStringIgnoreCase() throws Exception {
    server.requestHandler(req -> {
      assertTrue(req.headers().contains("Foo", "foo", true));
      assertTrue(req.headers().contains("Foo", "fOo", true));
      req.response().putHeader("quux", "quux");
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.putHeader("foo", "foo").send())
      .onComplete(onSuccess(resp -> {
        assertTrue(resp.headers().contains("Quux", "quux", true));
        assertTrue(resp.headers().contains("Quux", "quUx", true));
        testComplete();
      }));
    await();
  }

  @Test
  public void testContainsValueCharSequence() throws Exception {
    CharSequence Foo = HttpHeaders.createOptimized("Foo");
    CharSequence foo = HttpHeaders.createOptimized("foo");
    CharSequence fOo = HttpHeaders.createOptimized("fOo");

    CharSequence Quux = HttpHeaders.createOptimized("Quux");
    CharSequence quux = HttpHeaders.createOptimized("quux");
    CharSequence quUx = HttpHeaders.createOptimized("quUx");

    server.requestHandler(req -> {
      assertTrue(req.headers().contains(Foo, foo, false));
      assertFalse(req.headers().contains(Foo, fOo, false));
      req.response().putHeader(quux, quux);
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.putHeader(foo, foo).send())
      .onComplete(onSuccess(resp -> {
        assertTrue(resp.headers().contains(Quux, quux, false));
        assertFalse(resp.headers().contains(Quux, quUx, false));
        testComplete();
      }));
    await();
  }

  @Test
  public void testContainsValueCharSequenceIgnoreCase() throws Exception {
    CharSequence Foo = HttpHeaders.createOptimized("Foo");
    CharSequence foo = HttpHeaders.createOptimized("foo");
    CharSequence fOo = HttpHeaders.createOptimized("fOo");

    CharSequence Quux = HttpHeaders.createOptimized("Quux");
    CharSequence quux = HttpHeaders.createOptimized("quux");
    CharSequence quUx = HttpHeaders.createOptimized("quUx");

    server.requestHandler(req -> {
      assertTrue(req.headers().contains(Foo, foo, true));
      assertTrue(req.headers().contains(Foo, fOo, true));
      req.response().putHeader(quux, quux);
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.putHeader(foo, foo).send())
      .onComplete(onSuccess(resp -> {
        assertTrue(resp.headers().contains(Quux, quux, true));
        assertTrue(resp.headers().contains(Quux, quUx, true));
        testComplete();
      }));
    await();
  }

  @Test
  public void testBytesReadRequest() throws Exception {
    int length = 2048;
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(length));;
    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(req.bytesRead(), length);
        req.response().end();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req.send(expected).onSuccess(resp -> {
        resp.bodyHandler(buff -> {
          testComplete();
        });
      });
    }));
    await();
  }

  @Test
  public void testClientSynchronousConnectFailures() {
    System.setProperty("vertx.disableDnsResolver", "true");
    Vertx vertx = Vertx.vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setQueryTimeout(100)));
    try {
      int poolSize = 2;
      client.close();
      client = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions().setHttp1MaxSize(poolSize));
      AtomicInteger failures = new AtomicInteger();
      vertx.runOnContext(v -> {
        for (int i = 0; i < (poolSize + 1); i++) {
          AtomicBoolean f = new AtomicBoolean();
          client.request(new RequestOptions().setAbsoluteURI("http://invalid-host-name.foo.bar"))
            .onComplete(onFailure(req -> {
              if (f.compareAndSet(false, true)) {
                if (failures.incrementAndGet() == poolSize + 1) {
                  testComplete();
                }
              }
            }));
        }
      });
      await();
    } finally {
      vertx.close();
      System.setProperty("vertx.disableDnsResolver", "false");
    }
  }

  @Test
  public void testClientConnectInvalidPort() {
    try {
      client.request(new RequestOptions(requestOptions).setPort(-1));
    } catch (Exception e) {
      assertEquals(e.getClass(), IllegalArgumentException.class);
      assertEquals(e.getMessage(), "port p must be in range 0 <= p <= 65535");
    }
  }

  protected File setupFile(String fileName, String content) throws Exception {
    File file = new File(testDir, fileName);
    if (file.exists()) {
      file.delete();
    }
    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    out.write(content);
    out.close();
    return file;
  }

  protected static String generateQueryString(MultiMap params, char delim) {
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (Map.Entry<String, String> param : params.entries()) {
      sb.append(param.getKey()).append("=").append(param.getValue());
      if (++count != params.size()) {
        sb.append(delim);
      }
    }
    return sb.toString();
  }

  @Test
  public void testHttpClientRequestHeadersDontContainCROrLF() throws Exception {
    server.requestHandler(req -> {
      req.headers().forEach(header -> {
        String name = header.getKey();
        switch (name.toLowerCase()) {
          case "host":
          case ":method":
          case ":path":
          case ":scheme":
          case ":authority":
            break;
          default:
            fail("Unexpected header " + name);
        }
      });
      testComplete();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      List<BiConsumer<String, String>> list = Arrays.asList(
        req::putHeader,
        req.headers()::set,
        req.headers()::add
      );
      list.forEach(cs -> {
        try {
          req.putHeader("header-name: header-value\r\nanother-header", "another-value");
          fail();
        } catch (IllegalArgumentException e) {
        }
      });
      assertEquals(0, req.headers().size());
      req.end();
    }));
    await();
  }

  @Test
  public void testHttpServerResponseHeadersDontContainCROrLF() throws Exception {
    server.requestHandler(req -> {
      List<BiConsumer<String, String>> list = Arrays.asList(
        req.response()::putHeader,
        req.response().headers()::set,
        req.response().headers()::add
      );
      list.forEach(cs -> {
        try {
          cs.accept("header-name: header-value\r\nanother-header", "another-value");
          fail();
        } catch (IllegalArgumentException e) {
        }
      });
      assertEquals(Collections.emptySet(), req.response().headers().names());
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        resp.headers().forEach(header -> {
          String name = header.getKey();
          switch (name.toLowerCase()) {
            case "content-length":
              break;
            default:
              fail("Unexpected header " + name);
          }
        });
        testComplete();
      }));
    await();
  }

  @Test
  public void testDisableIdleTimeoutInPool() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions()
      .setIdleTimeout(1)
      .setKeepAliveTimeout(10), new PoolOptions().setHttp1MaxSize(1));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.endHandler(v1 -> {
          AtomicBoolean closed = new AtomicBoolean();
          resp.request().connection().closeHandler(v2 -> {
            closed.set(true);
          });
          vertx.setTimer(2000, id -> {
            assertFalse(closed.get());
            testComplete();
          });
        });
      }));
    }));
    await();
  }

  @Test
  public void testKeepAliveTimeout() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    HttpClientOptions options = createBaseClientOptions()
      .setKeepAliveTimeout(3)
      .setHttp2KeepAliveTimeout(3);
    testKeepAliveTimeout(options, new PoolOptions(), 1);
  }

  protected void testKeepAliveTimeout(HttpClientOptions options, PoolOptions poolOptions, int numReqs) throws Exception {
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(options, poolOptions.setCleanerPeriod(1));
    AtomicInteger respCount = new AtomicInteger();
    for (int i = 0;i < numReqs;i++) {
      int current = 1 + i;
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          respCount.incrementAndGet();
          if (current == numReqs) {
            long now = System.currentTimeMillis();
            resp.request().connection().closeHandler(v -> {
              long timeout = System.currentTimeMillis() - now;
              int delta = 500;
              int low = 3000 - delta;
              int high = 3000 + delta;
              assertTrue("Expected actual close timeout " + timeout + " to be > " + low, low < timeout);
              assertTrue("Expected actual close timeout " + timeout + " + to be < " + high, timeout < high);
              testComplete();
            });
          }
        }));
    }
    await();
  }

  @Test
  public void testPoolNotExpiring1() throws Exception {
    testPoolNotExpiring(createBaseClientOptions().setKeepAliveTimeout(100).setHttp2KeepAliveTimeout(100), new PoolOptions().setCleanerPeriod(0));
  }

  @Test
  public void testPoolNotExpiring2() throws Exception {
    testPoolNotExpiring(createBaseClientOptions().setKeepAliveTimeout(0).setHttp2KeepAliveTimeout(0), new PoolOptions().setCleanerPeriod(10));
  }

  private void testPoolNotExpiring(HttpClientOptions options, PoolOptions poolOptions) throws Exception {
    AtomicLong now = new AtomicLong();
    server.requestHandler(req -> {
      req.response().end();
      now.set(System.currentTimeMillis());
      vertx.setTimer(2000, id -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(options, poolOptions);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> req.send().onComplete(onSuccess(resp -> {
        resp.endHandler(v1 -> {
          resp.request().connection().closeHandler(v2 -> {
            long time = System.currentTimeMillis() - now.get();
            assertTrue("Was expecting " + time + " to be > 2000", time >= 2000);
            testComplete();
          });
        });
      }))));
    await();
  }

  @Test
  public void testHttpConnect() {
    testHttpConnect(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT), 200);
  }

  protected void testHttpConnect(RequestOptions options, int sc) {
    Buffer buffer = TestUtils.randomBuffer(128);
    Buffer received = Buffer.buffer();
    CompletableFuture<Void> closeSocket = new CompletableFuture<>();
    vertx.createNetServer(new NetServerOptions().setPort(1235).setHost("localhost")).connectHandler(socket -> {
      socket.handler(socket::write);
      closeSocket.thenAccept(v -> {
        socket.close();
      });
    }).listen().onComplete(onSuccess(netServer -> {
      server.requestHandler(req -> {
        vertx.createNetClient(new NetClientOptions()).connect(1235, "localhost").onComplete(onSuccess(dst -> {

          req.response().setStatusCode(sc);
          req.response().setStatusMessage("Connection established");

          // Now create a NetSocket
          req.toNetSocket().onComplete(onSuccess(src -> {
            // Create pumps which echo stuff
            src.pipeTo(dst);
            dst.pipeTo(src);
            dst.closeHandler(v -> {
              src.close();
            });
          }));
        }));
      });
      server.listen(testAddress).onComplete(onSuccess(s -> {
        client.request(options).onComplete(onSuccess(req -> {
          req
            .connect().onComplete(onSuccess(resp -> {
              assertEquals(sc, resp.statusCode());
              NetSocket socket = resp.netSocket();
              socket.handler(buff -> {
                received.appendBuffer(buff);
                if (received.length() == buffer.length()) {
                  closeSocket.complete(null);
                }
              });
              socket.closeHandler(v -> {
                assertEquals(buffer, received);
                testComplete();
              });
              socket.write(buffer);
            }));
        }));
      }));
    }));

    await();
  }

  @Test
  public void testNetSocketConnectSuccessClientInitiatesCloseImmediately() throws Exception {
    testNetSocketConnectSuccess(so -> {
      Buffer received = Buffer.buffer();
      so.handler(received::appendBuffer);
      so.endHandler(v -> {
        assertEquals("hello", received.toString());
        so.end();
        complete();
      });
    }, so -> {
      Buffer received = Buffer.buffer();
      so.handler(received::appendBuffer);
      so.endHandler(v -> {
        assertEquals("", received.toString());
        complete();
      });
      so.end(Buffer.buffer("hello"));
    });
  }

  @Test
  public void testNetSocketConnectSuccessServerInitiatesCloseImmediately() throws Exception {
    testNetSocketConnectSuccess(so -> {
      Buffer received = Buffer.buffer();
      so.handler(received::appendBuffer);
      so.endHandler(v -> {
        assertEquals("", received.toString());
        complete();
      });
      so.end(Buffer.buffer("hello"));
    }, so -> {
      Buffer received = Buffer.buffer();
      so.handler(received::appendBuffer);
      so.endHandler(v -> {
        assertEquals("hello", received.toString());
        so.end();
        complete();
      });
    });
  }

  @Test
  public void testNetSocketConnectSuccessServerInitiatesCloseOnReply() throws Exception {
    testNetSocketConnectSuccess(so -> {
      Buffer received = Buffer.buffer();
      so.handler(received::appendBuffer);
      so.endHandler(v -> {
        assertEquals("pong", received.toString());
        so.end();
        complete();
      });
      so.write(Buffer.buffer("ping"));
      }, so -> {
      so.handler(buff -> {
        if (buff.toString().equals("ping")) {
          so.end(Buffer.buffer("pong"));
        }
      });
      so.endHandler(v -> {
        complete();
      });
    });
  }

  @Test
  public void testNetSocketConnectSuccessClientInitiatesCloseOnReply() throws Exception {
    testNetSocketConnectSuccess(so -> {
      so.handler(buff -> {
        if (buff.toString().equals("ping")) {
          so.end(Buffer.buffer("pong"));
        }
      });
      so.endHandler(v -> {
        complete();
      });
    }, so -> {
      Buffer received = Buffer.buffer();
      so.handler(received::appendBuffer);
      so.endHandler(v -> {
        assertEquals("pong", received.toString());
        so.end();
        complete();
      });
      so.write(Buffer.buffer("ping"));
    });
  }

  private void testNetSocketConnectSuccess(Handler<NetSocket> clientHandler, Handler<NetSocket> serverHandler) throws Exception {
    waitFor(2);

    server.requestHandler(req -> {
      req.response().setStatusCode(101);
      req.toNetSocket().onComplete(onSuccess(serverHandler::handle));
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT))
      .onComplete(onSuccess(req -> {
      req.connect().onComplete(onSuccess(resp -> clientHandler.handle(resp.netSocket())));
    }));

    await();
  }

  @Test
  public void testClientNetSocketConnectReject() throws Exception {
    server.requestHandler(req -> {
      req.response().setStatusCode(404).end();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT)).onComplete(onSuccess(req -> {
      req.connect().onComplete(onSuccess(resp -> {
        assertEquals(404, resp.statusCode());
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testClientNetSocketConnectFailure() throws Exception {
    server.requestHandler(req -> {
      req.connection().close();
    });

    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT)).onComplete(onSuccess(req -> {
      req.send().onComplete(onFailure(err -> {
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testAccessNetSocketPendingResponseDataPaused() throws Exception {
    testAccessNetSocketPendingResponseData(true);
  }

  @Test
  public void testAccessNetSocketPendingResponseDataNotPaused() throws Exception {
    testAccessNetSocketPendingResponseData(false);
  }

  private void testAccessNetSocketPendingResponseData(boolean pause) throws Exception {
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(onSuccess(so -> {
        so.write("hello");
      }));
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT)).onComplete(onSuccess(req -> {
      req.connect().onComplete(onSuccess(so -> {
        assertNotNull(so);
        so.handler(buff -> {
          // With HTTP/1.1 the buffer is received immediately but delivered asynchronously
          assertEquals("hello", buff.toString());
          testComplete();
        });
        if (pause) {
          so.pause();
          vertx.setTimer(100, id -> {
            so.resume();
          });
        }
      }));
    }));

    await();
  }

  @Test
  public void testServerNetSocketCloseWithHandler() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(onSuccess(so -> {
        so.close().onComplete(onSuccess(v -> {
          complete();
        }));
      }));
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT)).onComplete(onSuccess(req -> {
      req.connect().onComplete(onSuccess(resp -> {
        NetSocket so = resp.netSocket();
        so.closeHandler(v -> {
          complete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testClientNetSocketCloseWithHandler() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.toNetSocket().onComplete(onSuccess(so -> {
        so.closeHandler(v -> {
          complete();
        });
      }));
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT)).onComplete(onSuccess(req -> {
      req.connect().onComplete(onSuccess(resp -> {
        NetSocket so = resp.netSocket();
        so.close().onComplete(onSuccess(v -> {
          complete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testHttpInvalidConnectResponseEnded() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
      req.toNetSocket().onComplete(onFailure(err -> {
        complete();
      }));
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }));

    await();
  }

  @Test
  public void testHttpInvalidConnectResponseChunked() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().setChunked(true).write("some-chunk");
      req.toNetSocket().onComplete(onFailure(err -> {
        complete();
      }));
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.CONNECT))
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }));

    await();
  }

  @Test
  public void testUpgradeTunnelNoSwitch() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      resp.write("chunk-1")
        .compose(v -> resp.write("chunk-2"))
        .compose(v -> resp.end("chunk-3"));
    });

    startServer();

    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/").onComplete(onSuccess(req -> {
      req.connect().onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        List<String> chunks = new ArrayList<>();
        resp.handler(chunk -> {
          chunks.add(chunk.toString());
        });
        resp.endHandler(v -> {
          assertEquals(Arrays.asList("chunk-1", "chunk-2", "chunk-3"), chunks);
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testEndFromAnotherThread() throws Exception {
    waitFor(2);
    disableThreadChecks();
    server.requestHandler(req -> {
      req.response().endHandler(v -> {
        complete();
      });
      new Thread(() -> {
        req.response().end();
      }).start();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }));

    await();
  }

  @Test
  public void testServerResponseWriteSuccess() throws Exception {
    testServerResponseWriteSuccess((resp, handler) -> resp.write(TestUtils.randomBuffer(1024)).onComplete(handler));
  }

  @Test
  public void testServerResponseEndSuccess() throws Exception {
    testServerResponseWriteSuccess((resp, handler) -> resp.end(TestUtils.randomBuffer(1024)).onComplete(handler));
  }

  private void testServerResponseWriteSuccess(BiConsumer<HttpServerResponse, Handler<AsyncResult<Void>>> op) throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      op.accept(resp, onSuccess(v -> {
        complete();
      }));
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> complete()));
    }));
    await();
  }

  @Test
  public void testServerResponseWriteFailure() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      Buffer chunk = randomBuffer(1024);
      Runnable[] task = new Runnable[1];
      task[0] = () -> {
        resp.write(chunk).onComplete(ar1 -> {
          if (ar1.succeeded()) {
            task[0].run();
          } else {
            resp.end().onComplete(ar2 -> {
              testComplete();
            });
          }
        });
      };
      task[0].run();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.request().connection().close();
      }));
    }));
    await();
  }

  @Test
  public void testClientRequestWriteSuccess() throws Exception {
    testClientRequestWriteSuccess((req, handler) -> {
      req.setChunked(true);
      req.write(TestUtils.randomBuffer(1024)).onComplete(handler);
      req.end();
    });
  }

  @Test
  public void testClientRequestEnd1Success() throws Exception {
    testClientRequestWriteSuccess((req, handler) -> req.end(TestUtils.randomBuffer(1024)).onComplete(handler));
  }

  @Test
  public void testClientRequestEnd2Success() throws Exception {
    testClientRequestWriteSuccess((req, handler) -> req.end().onComplete(handler));
  }

  private void testClientRequestWriteSuccess(BiConsumer<HttpClientRequest, Handler<AsyncResult<Void>>> op) throws Exception {

    waitFor(2);
    CompletableFuture<Void> fut = new CompletableFuture<>();
    server.requestHandler(req -> {
      fut.complete(null);
      req.endHandler(v -> {
        HttpServerResponse resp = req.response();
        if (!resp.ended()) {
          resp.end();
        }
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> {
          complete();
        }));
        op.accept(req, onSuccess(v -> {
          complete();
        }));
      }));
    await();
  }

  @Test
  public void testClientRequestLazyWriteSuccess() throws Exception {
    testClientRequestLazyWriteSuccess((resp, handler) -> resp.write(TestUtils.randomBuffer(1024)).onComplete(handler));
  }

  @Test
  public void testClientRequestLazyEndSuccess() throws Exception {
    testServerResponseWriteSuccess((resp, handler) -> resp.end(TestUtils.randomBuffer(1024)).onComplete(handler));
  }

  private void testClientRequestLazyWriteSuccess(BiConsumer<HttpClientRequest, Handler<AsyncResult<Void>>> op) throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT))
      .onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> complete()));
        req.setChunked(true);
        op.accept(req, onSuccess(v -> {
          complete();
        }));
      }));
    await();
  }

  @Test
  public void testClientResponseWriteFailure() throws Exception {
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req.setChunked(true);
      Buffer chunk = randomBuffer(1024);
      Handler<Void>[] task = new Handler[1];
      task[0] = v -> {
        req.write(chunk).onComplete(ar1 -> {
          if (ar1.succeeded()) {
            vertx.runOnContext(task[0]);
          } else {
            req.end().onComplete(ar2 -> {
              testComplete();
            });
          }
        });
      };
      task[0].handle(null);
    }));
    await();
  }

  @Test
  public void testServerRequestBodyFuture() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1024));
    server.requestHandler(req -> {
      req.body().onComplete(onSuccess(body -> {
        assertEquals(expected, body);
        req.response().end();
      }));
    });
    startServer(testAddress);
    client.request(requestOptions)
      .onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(v -> {
          testComplete();
        }));
        req.end(expected);
      }));
    await();
  }

  @Test
  public void testServerRequestBodyFutureFail() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1024));
    CompletableFuture<Void> latch = new CompletableFuture<>();
    server.requestHandler(req -> {
      req.body().onComplete(onFailure(err -> {
        testComplete();
      }));
      latch.complete(null);
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.setChunked(true);
      latch.whenComplete((v, err) -> {
        req.reset();
      });
      req.write(expected);
    }));
    await();
  }

  @Test
  public void testResetClientRequestBeforeActualSend() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.response().onComplete(onFailure(err -> {
        assertTrue(err instanceof StreamResetException);
        complete();
      }));
      req.exceptionHandler(err -> {
        assertTrue(err instanceof StreamResetException);
        complete();
      }).reset();
    }));
    await();
  }

  @Test
  public void testResetFromNonVertxThread() throws Exception {
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      client.request(requestOptions)
        .onComplete(onSuccess(req -> {
          req.exceptionHandler(err -> {
            assertSame(Vertx.currentContext(), ctx);
            testComplete();
          });
          new Thread(req::reset).start();
      }));
    });
    await();
  }

  @Test
  public void testResetClientRequestInProgress() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.response().onComplete(onFailure(err -> complete()));
        req.exceptionHandler(err -> complete());
        req.sendHead().onComplete(onSuccess(version -> req.reset(0)));
      }));
    });
    await();
  }

  @Test
  public void testResetClientRequestAwaitingResponse() throws Exception {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    server.requestHandler(req -> {
      fut.complete(null);
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      Context ctx = Vertx.currentContext();
      req.response().onComplete(onFailure(err -> complete()));
      req.exceptionHandler(err -> fail());
      req.end();
      fut.thenAccept(v2 -> {
        ctx.runOnContext(v3 -> {
          req.reset(0);
        });
      });
    }));
    await();
  }

  @Test
  public void testResetClientRequestResponseInProgress() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      for (int i = 0;i < 16;i++) {
        resp.write("chunk-" + i);
      }
      resp.end();
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        resp.handler(buff -> {
          fail();
        });
        resp.endHandler(v -> {
          fail();
        });
        req.connection().close().onComplete(onSuccess(v -> {
          testComplete();
        }));
        req.reset();
      }));
    }));
    await();
  }

  @Test
  public void testSimpleCookie() throws Exception {
    testCookies("foo=bar", req -> {
      assertEquals(1, req.cookieCount());
      Cookie cookie = req.getCookie("foo");
      assertNotNull(cookie);
      assertEquals("bar", cookie.getValue());
      req.response().end();
    }, response -> {
    });
  }

  @Test
  public void testGetCookies() throws Exception {
    testCookies("foo=bar; wibble=blibble; plop=flop", req -> {
      assertEquals(3, req.cookieCount());
      Map<String, Cookie> cookies = req.cookieMap();
      assertTrue(cookies.containsKey("foo"));
      assertTrue(cookies.containsKey("wibble"));
      assertTrue(cookies.containsKey("plop"));
      Cookie removed = req.response().removeCookie("foo");
      cookies = req.cookieMap();
      // removed cookies, need to be sent back with an expiration date
      assertTrue(cookies.containsKey("foo"));
      assertTrue(cookies.containsKey("wibble"));
      assertTrue(cookies.containsKey("plop"));
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      // the expired cookie must be sent back
      assertEquals(1, cookies.size());
      assertTrue(cookies.get(0).contains("Max-Age=0"));
      assertTrue(cookies.get(0).contains("Expires="));
    });
  }

  @Test
  public void testGetCookiesSameIdentity() throws Exception {
    testCookies(null, req -> {
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub").setDomain("www.vertx.io")));
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(3, cookies.size());
      assertTrue(cookies.contains("foo=bar; Path=/"));
      assertTrue(cookies.contains("foo=bar; Path=/sub"));
      assertTrue(cookies.contains("foo=bar; Path=/sub; Domain=www.vertx.io"));
    });
  }

  @Test
  public void testGetCookiesSameIdentityRemoveOne() throws Exception {
    testCookies(null, req -> {
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub").setDomain("www.vertx.io")));
      // will remove the first one only
      req.response().removeCookie("foo");
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(2, cookies.size());
      assertTrue(cookies.contains("foo=bar; Path=/sub"));
      assertTrue(cookies.contains("foo=bar; Path=/sub; Domain=www.vertx.io"));
    });
  }

  @Test
  public void testGetCookiesSameIdentityRemoveAll() throws Exception {
    testCookies(null, req -> {
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub").setDomain("www.vertx.io")));
      req.response().removeCookies("foo");
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(0, cookies.size());
    });
  }

  @Test
  public void testGetCookiesSameIdentityReplace() throws Exception {
    testCookies(null, req -> {
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub")));
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar").setPath("/sub").setDomain("www.vertx.io")));
      // will replace the last only
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "barista").setPath("/sub").setDomain("www.vertx.io")));
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(3, cookies.size());
      assertTrue(cookies.contains("foo=bar; Path=/"));
      assertTrue(cookies.contains("foo=bar; Path=/sub"));
      assertTrue(cookies.contains("foo=barista; Path=/sub; Domain=www.vertx.io"));
    });
  }

  @Test
  public void testCookiesChanged() throws Exception {
    testCookies("foo=bar; wibble=blibble; plop=flop", req -> {
      assertEquals(3, req.cookieCount());
      assertEquals("bar", req.getCookie("foo").getValue());
      assertEquals("blibble", req.getCookie("wibble").getValue());
      assertEquals("flop", req.getCookie("plop").getValue());
      req.response().removeCookie("plop");
      // the expected number of elements should remain the same as we're sending an invalidate cookie back
      assertEquals(3, req.cookieCount());

      assertEquals("bar", req.getCookie("foo").getValue());
      assertEquals("blibble", req.getCookie("wibble").getValue());
      assertNotNull(req.getCookie("plop"));
      req.response().addCookie(Cookie.cookie("fleeb", "floob"));
      assertEquals(4, req.cookieCount());
      assertNull(req.response().removeCookie("blarb"));
      assertEquals(4, req.cookieCount());
      Cookie foo = req.getCookie("foo");
      foo.setValue("blah");
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(3, cookies.size());
      assertTrue(cookies.contains("foo=blah"));
      assertTrue(cookies.contains("fleeb=floob"));
      boolean found = false;
      for (String s : cookies) {
        if (s.startsWith("plop")) {
          found = true;
          assertTrue(s.contains("Max-Age=0"));
          assertTrue(s.contains("Expires="));
          break;
        }
      }
      assertTrue(found);
    });
  }

  @Test
  public void testCookieFields() throws Exception {
    Cookie cookie = Cookie.cookie("foo", "bar");
    assertEquals("foo", cookie.getName());
    assertEquals("bar", cookie.getValue());
    assertEquals("foo=bar", cookie.encode());
    assertNull(cookie.getPath());
    cookie.setPath("/somepath");
    assertEquals("/somepath", cookie.getPath());
    assertEquals("foo=bar; Path=/somepath", cookie.encode());
    assertNull(cookie.getDomain());
    cookie.setDomain("foo.com");
    assertEquals("foo.com", cookie.getDomain());
    assertEquals("foo=bar; Path=/somepath; Domain=foo.com", cookie.encode());
    long maxAge = 30 * 60;
    cookie.setMaxAge(maxAge);
    assertEquals(maxAge, cookie.getMaxAge());

    long now = System.currentTimeMillis();
    String encoded = cookie.encode();
    int startPos = encoded.indexOf("Expires=");
    int endPos = encoded.indexOf(';', startPos);
    String expiresDate = encoded.substring(startPos + 8, endPos);
    // RFC1123
    DateFormat dtf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
    dtf.setTimeZone(TimeZone.getTimeZone("GMT"));
    Date d = dtf.parse(expiresDate);
    assertTrue(d.getTime() - now >= maxAge);

    cookie.setMaxAge(Long.MIN_VALUE);
    cookie.setSecure(true);
    assertTrue(cookie.isSecure());
    assertEquals("foo=bar; Path=/somepath; Domain=foo.com; Secure", cookie.encode());
    cookie.setHttpOnly(true);
    assertTrue(cookie.isHttpOnly());
    assertEquals("foo=bar; Path=/somepath; Domain=foo.com; Secure; HTTPOnly", cookie.encode());
  }

  @Test
  public void testCookieSameSiteFieldEncoding() {
    Cookie cookie = Cookie.cookie("foo", "bar").setSameSite(CookieSameSite.LAX);
    assertEquals("foo", cookie.getName());
    assertEquals("bar", cookie.getValue());
    assertEquals(CookieSameSite.LAX, cookie.getSameSite());
    assertEquals("foo=bar; SameSite=Lax", cookie.encode());

    cookie.setSecure(true);
    assertEquals("foo=bar; Secure; SameSite=Lax", cookie.encode());
    cookie.setHttpOnly(true);
    assertEquals("foo=bar; Secure; HTTPOnly; SameSite=Lax", cookie.encode());
  }

  @Test
  public void testCookieSameSiteFieldValidation() throws Exception {
    Cookie cookie = Cookie.cookie("foo", "bar");

    try {
      cookie.setSameSite(CookieSameSite.LAX);
      // OK
      cookie.setSameSite(CookieSameSite.STRICT);
      // OK
      cookie.setSameSite(CookieSameSite.NONE);
      // OK
      cookie.setSameSite(null);
      // OK
    } catch (RuntimeException e) {
      fail();
    }
  }

  @Test
  public void testRemoveCookies() throws Exception {
    testCookies("foo=bar", req -> {
      Cookie removed = req.response().removeCookie("foo");
      assertNotNull(removed);
      assertEquals("foo", removed.getName());
      assertEquals("", removed.getValue());
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      // the expired cookie must be sent back
      assertEquals(1, cookies.size());
      assertTrue(cookies.get(0).contains("foo="));
      assertTrue(cookies.get(0).contains("Max-Age=0"));
      assertTrue(cookies.get(0).contains("Expires="));
    });
  }

  @Test
  public void testNoCookiesRemoveCookie() throws Exception {
    testCookies(null, req -> {
      req.response().removeCookie("foo");
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(0, cookies.size());
    });
  }

  @Test
  public void testNoCookiesCookieCount() throws Exception {
    testCookies(null, req -> {
      assertEquals(0, req.cookieCount());
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(0, cookies.size());
    });
  }

  @Test
  public void testNoCookiesGetCookie() throws Exception {
    testCookies(null, req -> {
      assertNull(req.getCookie("foo"));
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(0, cookies.size());
    });
  }

  @Test
  public void testNoCookiesAddCookie() throws Exception {
    testCookies(null, req -> {
      assertEquals(req.response(), req.response().addCookie(Cookie.cookie("foo", "bar")));
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      assertEquals(1, cookies.size());
    });
  }

  @Test
  public void testReplaceCookie() throws Exception {
    testCookies("XSRF-TOKEN=c359b44aef83415", req -> {
      assertEquals(1, req.cookieCount());
      req.response().addCookie(Cookie.cookie("XSRF-TOKEN", "88533580000c314").setPath("/"));
      Map<String, Cookie> deprecatedMap = req.cookieMap();
      assertFalse(((ServerCookie) deprecatedMap.get("XSRF-TOKEN")).isFromUserAgent());
      assertEquals("/", deprecatedMap.get("XSRF-TOKEN").getPath());
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      // the expired cookie must be sent back
      assertEquals(1, cookies.size());
      // ensure that the cookie jar was updated correctly
      assertEquals("XSRF-TOKEN=88533580000c314; Path=/", cookies.get(0));
    });
  }

  private void testCookies(String cookieHeader, Consumer<HttpServerRequest> serverChecker, Consumer<HttpClientResponse> clientChecker) throws Exception {
    server.requestHandler(serverChecker::accept);
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.putHeader(HttpHeaders.COOKIE.toString(), cookieHeader).send())
      .onComplete(onSuccess(resp -> {
        clientChecker.accept(resp);
        testComplete();
      }));
    await();
  }

  @Test
  public void testClientRequestFutureSetHandlerFromAnotherThread() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    client.close();
    Context ctx = vertx.getOrCreateContext();
    CompletableFuture<HttpClientRequest> reqFut = new CompletableFuture<>();
    ctx.runOnContext(v -> {
      client = vertx.createHttpClient(createBaseClientOptions());
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.response().onComplete(onSuccess(resp -> {
          complete();
        }));
        reqFut.complete(req);
      }));
    });
    HttpClientRequest req = reqFut.get(10, TimeUnit.SECONDS);
    Future<Void> endFut = req.end("msg");
    waitUntil(endFut::succeeded);
    // Set the handler after the future
    endFut.onComplete(onSuccess(v -> {
      assertNotNull(Vertx.currentContext());
      assertSameEventLoop(ctx, Vertx.currentContext());
      complete();
    }));
    await();
  }

  @Test
  public void testClientRequestWithLargeBodyInSmallChunks() throws Exception {
    testClientRequestWithLargeBodyInSmallChunks(false, HttpClientRequest::send);
  }

  @Test
  public void testClientRequestWithLargeBodyInSmallChunksChunked() throws Exception {
    testClientRequestWithLargeBodyInSmallChunks(true, HttpClientRequest::send);
  }

  @Test
  public void testClientRequestWithLargeBodyInSmallChunksWithHandler() throws Exception {
    testClientRequestWithLargeBodyInSmallChunks(false, HttpClientRequest::send);
  }

  @Test
  public void testClientRequestWithLargeBodyInSmallChunksChunkedWithHandler() throws Exception {
    testClientRequestWithLargeBodyInSmallChunks(true, HttpClientRequest::send);
  }

  private void testClientRequestWithLargeBodyInSmallChunks(boolean chunked, BiFunction<HttpClientRequest, ReadStream<Buffer>, Future<HttpClientResponse>> sendFunction) throws Exception {
    StringBuilder sb = new StringBuilder();
    FakeStream<Buffer> src = new FakeStream<>();
    src.pause();
    int numChunks = 1024;
    int chunkLength = 1024;
    for (int i = 0;i < numChunks;i++) {
      String chunk = randomAlphaString(chunkLength);
      sb.append(chunk);
      src.write(Buffer.buffer(chunk));
    }
    src.end();
    String expected = sb.toString();
    String contentLength = "" + numChunks * chunkLength;
    waitFor(2);
    server.requestHandler(req -> {
      assertEquals(chunked ? null : contentLength, req.getHeader(HttpHeaders.CONTENT_LENGTH));
      assertEquals(chunked & HttpVersion.isFrameBased(req.version()) ? HttpHeaders.CHUNKED.toString() : null, req.getHeader(HttpHeaders.TRANSFER_ENCODING));
      req.bodyHandler(body -> {
        assertEquals(HttpMethod.PUT, req.method());
        assertEquals(Buffer.buffer(expected), body);
        complete();
        req.response().end();
      });
    });
    startServer();
    RequestOptions options = new RequestOptions()
      .setMethod(HttpMethod.PUT)
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(DEFAULT_TEST_URI);
    client.request(options)
      .compose(req -> {
        if (!chunked) {
          req.putHeader(HttpHeaders.CONTENT_LENGTH, contentLength);
        }
        return sendFunction.apply(req,src);
      })
      .onComplete(onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      complete();
    }));
    await();
  }

  @Test
  public void testClientRequestFlowControlDifferentEventLoops() throws Exception {
    Promise<Void> resume = Promise.promise();
    server.requestHandler(req -> {
      req.pause();
      req.end().onComplete(v -> {
        req.response().end();
      });
      resume.future().onComplete(ar -> req.resume());
    });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions(), new PoolOptions().setHttp1MaxSize(1));
    Buffer chunk = Buffer.buffer(TestUtils.randomAlphaString(1024));
    client.request(requestOptions).onComplete(onSuccess(req1 -> {
      assertTrue(req1.reset());
      new Thread(() -> {
        Context ctx = vertx.getOrCreateContext();
        ctx.runOnContext(v1 -> {
          client.request(requestOptions).onComplete(onSuccess(req2 -> {
            assertSame(ctx, vertx.getOrCreateContext());
            req2.setChunked(true);
            while (!req2.writeQueueFull()) {
              req2.write(chunk);
            }
            resume.complete();
            req2.drainHandler(v -> {
              assertSame(ctx, vertx.getOrCreateContext());
              req2.end();
            });
            req2.response().onComplete(onSuccess(resp -> {
              assertSame(ctx, vertx.getOrCreateContext());
              resp.end().onComplete(onSuccess(v2 -> {
                assertSame(ctx, vertx.getOrCreateContext());
                testComplete();
              }));
            }));
          }));
        });
      }).start();
    }));
    await();
  }

  @Test
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    HAProxy proxy = new HAProxy(testAddress, Buffer.buffer());
    proxy.start(vertx);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().
      setProxyProtocolTimeout(2).
      setUseProxyProtocol(true));
    server.requestHandler(req -> fail("Should not be called"));
    startServer(testAddress);
    vertx.createNetClient().connect(proxy.getPort(), proxy.getHost()).onComplete(res -> {
      res.result().closeHandler(event -> testComplete());
    });
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    waitFor(2);
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);

    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().
      setProxyProtocolTimeout(100).
      setProxyProtocolTimeoutUnit(TimeUnit.MILLISECONDS).
      setUseProxyProtocol(true));
    server.requestHandler(req -> {
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.request(HttpMethod.GET, proxy.getPort(), proxy.getHost(), DEFAULT_TEST_URI)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(v -> complete()));
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion1UnknownProtocolHeader();
    testHAProxyProtocolAccepted(header, null, null);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixStreamProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    Assume.assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion2UnknownProtocolHeader();
    testHAProxyProtocolAccepted(header, null, null);
  }


  private void testHAProxyProtocolAccepted(Buffer header, SocketAddress remote, SocketAddress local) throws Exception {
    /*
     * In case remote / local is null then we will use the connected remote / local address from the proxy. This is needed
     * in order to test unknown protocol since we will use the actual connected addresses and ports.
     * This is only valid when testAddress is an InetSocketAddress. If testAddress is a DomainSocketAddress then
     * remoteAddress and localAddress are null
     *
     * Have in mind that proxies connectionRemoteAddress is the server request local address and proxies connectionLocalAddress is the
     * server request remote address.
     * */
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions()
      .setUseProxyProtocol(true))
      .requestHandler(req -> {
        assertAddresses(remote == null && testAddress.isInetSocket() ?
            proxy.getConnectionLocalAddress() :
            remote,
          req.remoteAddress());
        assertAddresses(local == null && testAddress.isInetSocket() ?
            proxy.getConnectionRemoteAddress() :
            local,
          req.localAddress());
        req.response().end();
        complete();
      });
    startServer(testAddress);

    client.request(new RequestOptions()
      .setHost(proxy.getHost())
      .setPort(proxy.getPort())
      .setURI(DEFAULT_TEST_URI)).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(event -> complete()));
    }));
    try {
      await();
    } finally {
      proxy.stop();
    }
  }


  @Test
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2UDP4ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  @Test
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2UDP6ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixDatagramProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  private void testHAProxyProtocolRejected(Buffer header) throws Exception {
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);
    server.close();

    server = vertx.createHttpServer(createBaseServerOptions()
      .setUseProxyProtocol(true))
      .exceptionHandler(ex -> {
        if (ex.equals(HAProxyMessageCompletionHandler.UNSUPPORTED_PROTOCOL_EXCEPTION))
          complete();
      })
      .requestHandler(req -> fail());

    startServer(testAddress);
    client.request(new RequestOptions()
      .setPort(proxy.getPort())
      .setHost(proxy.getHost())
      .setURI(DEFAULT_TEST_URI))
      .compose(HttpClientRequest::send)
      .onComplete(onFailure(req -> complete()));

    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolEmptyHeader() throws Exception {
    testHAProxyProtocolIllegal(Buffer.buffer());
  }

  @Test
  public void testHAProxyProtocolIllegalHeader() throws Exception {
    //IPv4 remote IPv6 Local
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolIllegal(header);
  }

  @Test
  public void testClientClose() throws Exception {
    AtomicInteger inflight = new AtomicInteger();
    int num = 3;
    List<HttpServer> servers = new ArrayList<>();
    try {
      for (int i = 0;i < num;i++) {
        HttpServer server = vertx.createHttpServer(createBaseServerOptions());
        server.requestHandler(req -> {
        });
        startServer(SocketAddress.inetSocketAddress(DEFAULT_HTTP_PORT + i, DEFAULT_HTTP_HOST), server);
        servers.add(server);
      }
      client.close();
      client = vertx.httpClientBuilder()
        .with(createBaseClientOptions())
        .withConnectHandler(conn -> {
          inflight.incrementAndGet();
          conn.closeHandler(v -> {
            inflight.decrementAndGet();
          });
        })
        .build();
      for (int i = 0;i < num;i++) {
        client.request(new RequestOptions()
          .setHost(DEFAULT_HTTP_HOST)
          .setPort(DEFAULT_HTTP_PORT + i))
          .onComplete(onSuccess(HttpClientRequest::send));
      }
      assertWaitUntil(() -> inflight.get() == num);
      CountDownLatch latch = new CountDownLatch(1);
      client.close().onComplete(onSuccess(v -> {
        latch.countDown();
      }));
      awaitLatch(latch);
      assertWaitUntil(() -> inflight.get() == 0);
    } finally {
      servers.forEach(HttpServer::close);
    }
  }

  private void testHAProxyProtocolIllegal(Buffer header) throws Exception {
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);
    server.close();

    server = vertx.createHttpServer(createBaseServerOptions()
      .setUseProxyProtocol(true))
      .exceptionHandler(ex -> {
        if (ex instanceof io.netty.handler.codec.haproxy.HAProxyProtocolException)
          complete();
      })
      .requestHandler(req -> fail());

    startServer(testAddress);
    client.request(new RequestOptions()
      .setPort(proxy.getPort())
      .setHost(proxy.getHost())
      .setURI(DEFAULT_TEST_URI))
      .compose(HttpClientRequest::send)
      .onComplete(onFailure(ex -> complete()));
    await();
    proxy.stop();
  }

  private void assertAddresses(SocketAddress address1, SocketAddress address2) {
    if (address1 == null || address2 == null)
      assertEquals(address1, address2);
    else {
      assertEquals(address1.hostAddress(), address2.hostAddress());
      assertEquals(address1.port(), address2.port());
    }
  }

  @Test
  public void testStickyEventLoops() throws Exception {
    Set<EventLoop> contexts = new HashSet<>();
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    int numReq = 3;
    CountDownLatch latch = new CountDownLatch(numReq);
    for (int i = 0;i < numReq;i++) {
      client.request(requestOptions).onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          contexts.add(((ContextInternal)Vertx.currentContext()).nettyEventLoop());
          latch.countDown();
        }));
      }));
    }
    awaitLatch(latch);
    assertEquals(1, contexts.size());
  }

  @Test
  public void testRetrySameHostOnCallbackFailure() {
    client.request(requestOptions).onComplete(onFailure(req1 -> {
      client.request(requestOptions).onComplete(onFailure(req2 -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testHttpServerEndHandlerSuccess() throws Exception {
    server.requestHandler(req -> {
      req.end().onComplete(onSuccess(v -> {
        req.response().end();
      }));
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req.send().map(resp -> resp.statusCode()))
      .onComplete(onSuccess(sc -> {
        assertEquals(200, (int)sc);
        testComplete();
      }));
    await();
  }

  @Test
  public void testHttpServerEndHandlerError() throws Exception {
    Promise<Void> promise = Promise.promise();
    server.requestHandler(req -> {
      promise.complete();
      req.end().onComplete(onFailure(err -> {
        testComplete();
      }));
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.PUT)).onComplete(onSuccess(req -> {
      req
        .setChunked(true)
        .sendHead();
      promise.future().onSuccess(v -> {
        req.connection().close();
      });
    }));
    await();
  }

  @Test
  public void testHttpClientEndHandlerSuccess() throws Exception {
    server.requestHandler(req -> {
      req.response().end("hello");
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req
      .send()
      .compose(HttpClientResponse::end))
      .onComplete(onSuccess(v -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testHttpClientEndHandlerFailure() throws Exception {
    Promise<Void> promise = Promise.promise();
    server.requestHandler(req -> {
      req
        .response()
        .setChunked(true)
        .write("hello");
      promise.future().onSuccess(v -> {
        req.connection().close();
      });
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req.send().compose(resp -> {
      promise.complete();
      return resp.end();
    }))
      .onComplete(onFailure(v -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testServerResponseSend() throws Exception {
    testServerResponseSend(false);
  }

  protected void testServerResponseSend(boolean chunked) throws Exception {
    int num = 16;
    List<Buffer> chunks = new ArrayList<>();
    Buffer expected = Buffer.buffer();
    for (int i = 0;i < num;i++) {
      Buffer chunk = Buffer.buffer("chunk-" + i);
      chunks.add(chunk);
      expected.appendBuffer(chunk);
    }
    String contentLength = "" + expected.length();
    server.requestHandler(req -> {
      FakeStream<Buffer> stream = new FakeStream<>();
      stream.pause();
      stream.emit(chunks.stream());
      stream.end();
      HttpServerResponse resp = req.response();
      if (!chunked) {
        resp.putHeader(HttpHeaders.CONTENT_LENGTH, contentLength);
      }
      resp.send(stream);
    });
    startServer(testAddress);
    client.request(requestOptions).compose(req -> req.send().compose(resp -> {
      if (!chunked) {
        assertEquals(contentLength, resp.getHeader(HttpHeaders.CONTENT_LENGTH));
      }
      List<Buffer> list = new ArrayList<>();
      resp.handler(buff -> {
        if (buff.length() > 0) {
          list.add(buff); // Last HTTP2/2 buffer is empty
        }
      });
      return resp.end().map(list);
    }))
      .onComplete(onSuccess(list -> {
        if (chunked) {
          assertEquals(num, list.size());
        }
        Buffer result = Buffer.buffer();
        list.forEach(result::appendBuffer);
        assertEquals(expected, result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testConnectTimeout() {
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setConnectTimeout(1));
    client.request(new RequestOptions().setHost(TestUtils.NON_ROUTABLE_HOST).setPort(HttpTestBase.DEFAULT_HTTP_PORT))
      .onComplete(onFailure(err -> {
        assertTrue(err instanceof ConnectTimeoutException);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResponseEndFutureCompletes_WithoutBody() throws Exception {
      testResponseEndFutureCompletes(HttpServerResponse::end);
  }

  @Test
  public void testResponseEndFutureCompletes_WithBody() throws Exception {
    testResponseEndFutureCompletes(httpServerResponse -> httpServerResponse.end("hello"));
  }

  @Test
  public void testResponseEndFutureCompletes_ChunkedWithoutBody() throws Exception {
    testResponseEndFutureCompletes(httpServerResponse -> httpServerResponse.setChunked(true).write("hello")
      .compose(nothing -> httpServerResponse.end())
    );
  }

  @Test
  public void testResponseEndFutureCompletes_ChunkedWithBody() throws Exception {
    testResponseEndFutureCompletes(httpServerResponse -> httpServerResponse.setChunked(true).write("hello")
      .compose(nothing -> httpServerResponse.end("world"))
    );
  }

  private void testResponseEndFutureCompletes(final Function<HttpServerResponse, Future<Void>> function) throws Exception {
    waitFor(2);
    server.requestHandler(
      httpServerRequest -> function.apply(httpServerRequest.response()).onComplete(onSuccess(nothing -> complete()))
    );
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .onComplete(onSuccess(nothing -> complete()));
    await();
  }

  @Test
  public void shouldThrowISEIfSendingResponseFromHeadersEndHandler() throws Exception {
    AtomicBoolean flag = new AtomicBoolean();
    waitFor(3);
    server.requestHandler(req -> {
        HttpServerResponse resp = req.response();
        resp.headersEndHandler(v -> {
          if (flag.compareAndSet(false, true)) {
            try {
              resp.end("bar");
            } catch (IllegalStateException e) {
              complete();
            }
          }
        });
        resp.end("foo");
        complete();
      }
    );
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .onComplete(onSuccess(nothing -> complete()));
    await();
  }

  @Test
  public void testInvalidPort() {
    try {
      server.requestHandler(req -> {

      }).listen(65536);
      fail();
    } catch (IllegalArgumentException ignore) {
    }
  }

  @Test
  public void testDnsClientSideLoadBalancingDisabled() throws Exception {
    testDnsClientSideLoadBalancing(false);
  }

  @Test
  public void testDnsClientSideLoadBalancingEnabled() throws Exception {
    testDnsClientSideLoadBalancing(true);
  }

  private void testDnsClientSideLoadBalancing(boolean enabled) throws Exception {
    FakeDNSServer server = new FakeDNSServer();
    server.store(question -> new HashSet<>(Arrays.asList(
      new FakeDNSServer.Record("vertx.io", RecordType.A, RecordClass.IN, 100)
        .set(DnsAttribute.IP_ADDRESS, "127.0.0.1"),
      new FakeDNSServer.Record("vertx.io", RecordType.A, RecordClass.IN, 100)
        .set(DnsAttribute.IP_ADDRESS, "127.0.0.2")
      )));
    server.start();
    AddressResolverOptions resolverOptions = new AddressResolverOptions()
      .addServer(server.localAddress().getAddress().getHostAddress() + ":" + server.localAddress().getPort());
    Vertx vertx = Vertx.vertx(new VertxOptions().setAddressResolverOptions(resolverOptions));
    try {
      AtomicInteger val = new AtomicInteger();
      HttpClient client = vertx
        .httpClientBuilder()
        .with(createBaseClientOptions())
        .withLoadBalancer(enabled ? endpoints -> () -> {
          val.set(endpoints.size());
          return 0;
        } : null)
        .build();
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "vertx.io", "/").onComplete(onFailure(err -> {
        assertEquals(enabled ? 2 : 0, val.get());
        testComplete();
      }));
      await();
    } finally {
      vertx.close().await();
      server.stop();
    }
  }

  @Test
  public void testConcurrentWrites1() throws Exception {
    testConcurrentWrites(req -> req
      .sendHead()
      .compose(v -> {
        AtomicBoolean latch = new AtomicBoolean(false);
        new Thread(() -> {
          req.write("msg1");
          latch.set(true); // Release Event-loop thread
        }).start();
        // Active wait for the event to be published
        while (!latch.get()) {
        }
        req.write("msg2");
        req.end();
        return req.response();
      }));
  }

  @Test
  public void testConcurrentWrites2() throws Exception {
    testConcurrentWrites(req -> {
      AtomicBoolean latch = new AtomicBoolean(false);
      new Thread(() -> {
        req.sendHead();
        latch.set(true); // Release Event-loop thread
      }).start();
      // Active wait for the event to be published
      while (!latch.get()) {
      }
      req.write("msg1");
      req.write("msg2");
      req.end();
      return req.response();
    });
  }

  private void testConcurrentWrites(Function<HttpClientRequest, Future<HttpClientResponse>> action) throws Exception {
    waitFor(1);
    AtomicReference<String> received = new AtomicReference<>();
    server.requestHandler(req -> req.body()
                                    .onSuccess(buffer -> {
                                      received.set(buffer.toString());
                                      req.response().end();
                                    }));
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions());
    client.request(requestOptions)
      .compose(req -> {
        req.setChunked(true);
        return action.apply(req);
      })
      .onComplete(onSuccess(resp -> complete()));
    await();
    assertEquals("msg1msg2", received.get());
  }
}
