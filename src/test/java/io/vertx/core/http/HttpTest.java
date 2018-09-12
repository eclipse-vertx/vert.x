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

import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Exception;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.impl.HeadersAdaptor;
import io.vertx.core.net.*;
import io.vertx.core.streams.Pump;
import io.vertx.test.core.TestUtils;
import io.vertx.test.netty.TestLoggerFactory;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static io.vertx.test.core.TestUtils.*;
import static java.util.Collections.singletonList;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpTest extends HttpTestBase {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  protected File testDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testDir = testFolder.newFolder();
  }

  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST);
  }

  protected HttpClientOptions createBaseClientOptions() {
    return new HttpClientOptions();
  }

  @Test
  public void testClientRequestArguments() throws Exception {
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
    assertNullPointerException(() -> req.putHeader((String) null, "someValue"));
    assertNullPointerException(() -> req.putHeader((CharSequence) null, "someValue"));
    assertNullPointerException(() -> req.putHeader("someKey", (Iterable<String>) null));
    assertNullPointerException(() -> req.write((Buffer) null));
    assertNullPointerException(() -> req.write((String) null));
    assertNullPointerException(() -> req.write(null, "UTF-8"));
    assertNullPointerException(() -> req.write("someString", null));
    assertNullPointerException(() -> req.end((Buffer) null));
    assertNullPointerException(() -> req.end((String) null));
    assertNullPointerException(() -> req.end(null, "UTF-8"));
    assertNullPointerException(() -> req.end("someString", null));
    assertIllegalArgumentException(() -> req.setTimeout(0));
  }

  @Test
  public void testClientChaining() {
    server.requestHandler(noOpHandler());

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
      assertTrue(req.setChunked(true) == req);
      assertTrue(req.sendHead() == req);
      assertTrue(req.write("foo", "UTF-8") == req);
      assertTrue(req.write("foo") == req);
      assertTrue(req.write(Buffer.buffer("foo")) == req);
      testComplete();
    }));

    await();
  }

  @Test
  public void testListenSocketAddress() {
    NetClient netClient = vertx.createNetClient();
    server = vertx.createHttpServer().requestHandler(req -> req.response().end());
    SocketAddress sockAddress = SocketAddress.inetSocketAddress(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
    server.listen(sockAddress, onSuccess(server -> {
      netClient.connect(sockAddress, onSuccess(sock -> {
        sock.handler(buf -> {
          assertTrue("Response is not an http 200", buf.toString("UTF-8").startsWith("HTTP/1.1 200 OK"));
          testComplete();
        });
        sock.write("GET / HTTP/1.1\r\n\r\n");
      }));
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
    NetClient netClient = vx.createNetClient();
    HttpServer httpserver = vx.createHttpServer().requestHandler(req -> req.response().end());
    File sockFile = TestUtils.tmpFile("vertx", ".sock");
    SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
    httpserver.listen(sockAddress, onSuccess(server -> {
      netClient.connect(sockAddress, onSuccess(sock -> {
        sock.handler(buf -> {
          assertTrue("Response is not an http 200", buf.toString("UTF-8").startsWith("HTTP/1.1 200 OK"));
          testComplete();
        });
        sock.write("GET / HTTP/1.1\r\n\r\n");
      }));
    }));

    try {
      await();
    } finally {
      vx.close();
    }
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
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
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
  public void testServerActualPortWhenSet() {
    server
        .requestHandler(request -> {
          request.response().end("hello");
        })
        .listen(ar -> {
          assertEquals(ar.result().actualPort(), DEFAULT_HTTP_PORT);
          vertx.createHttpClient(createBaseClientOptions()).getNow(ar.result().actualPort(), DEFAULT_HTTP_HOST, "/", response -> {
            assertEquals(response.statusCode(), 200);
            response.bodyHandler(body -> {
              assertEquals(body.toString("UTF-8"), "hello");
              testComplete();
            });
          });
        });
    await();
  }

  @Test
  public void testServerActualPortWhenZero() {
    server = vertx.createHttpServer(createBaseServerOptions().setPort(0).setHost(DEFAULT_HTTP_HOST));
    server
        .requestHandler(request -> {
          request.response().end("hello");
        })
        .listen(ar -> {
          assertTrue(ar.result().actualPort() != 0);
          vertx.createHttpClient(createBaseClientOptions()).getNow(ar.result().actualPort(), DEFAULT_HTTP_HOST, "/", response -> {
            assertEquals(response.statusCode(), 200);
            response.bodyHandler(body -> {
              assertEquals(body.toString("UTF-8"), "hello");
              testComplete();
            });
          });
        });
    await();
  }

  @Test
  public void testServerActualPortWhenZeroPassedInListen() {
    server = vertx.createHttpServer(new HttpServerOptions(createBaseServerOptions()).setHost(DEFAULT_HTTP_HOST));
    server
        .requestHandler(request -> {
          request.response().end("hello");
        })
        .listen(0, ar -> {
          assertTrue(ar.result().actualPort() != 0);
          vertx.createHttpClient(createBaseClientOptions()).getNow(ar.result().actualPort(), DEFAULT_HTTP_HOST, "/", response -> {
            assertEquals(response.statusCode(), 200);
            response.bodyHandler(body -> {
              assertEquals(body.toString("UTF-8"), "hello");
              testComplete();
            });
          });
        });
    await();
  }

  @Test
  public void testRequestNPE() {
    String uri = "/some-uri?foo=bar";
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri, null));
    TestUtils.assertNullPointerException(() -> client.request((HttpMethod)null, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri, resp -> {}));
    TestUtils.assertNullPointerException(() -> client.requestAbs((HttpMethod) null, "http://someuri", resp -> {
    }));
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, 8080, "localhost", "/somepath", null));
    TestUtils.assertNullPointerException(() -> client.request((HttpMethod) null, 8080, "localhost", "/somepath", resp -> {
    }));
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, 8080, null, "/somepath", resp -> {
    }));
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, 8080, "localhost", null, resp -> {
    }));
  }

  @Test
  public void testInvalidAbsoluteURI() {
    try {
      client.requestAbs(HttpMethod.GET, "ijdijwidjqwoijd192d192192ej12d", resp -> {
      }).end();
      fail("Should throw exception");
    } catch (VertxException e) {
      //OK
    }
  }

  @Test
  public void testPutHeadersOnRequest() {
    server.requestHandler(req -> {
      assertEquals("bar", req.headers().get("foo"));
      assertEquals("bar", req.getHeader("foo"));
      req.response().end();
    });
    server.listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }).putHeader("foo", "bar").end();
    }));
    await();
  }

  @Test
  public void testPutHeaderReplacesPreviousHeaders() throws Exception {
    server.requestHandler(req ->
      req.response()
        .putHeader("Location", "http://example1.org")
        .putHeader("location", "http://example2.org")
        .end());
    server.listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(singletonList("http://example2.org"), resp.headers().getAll("LocatioN"));
        testComplete();
       }).end();
    }));
    await();
  }

  @Test
  public void testSimpleGET() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.GET, resp -> testComplete());
  }

  @Test
  public void testSimplePUT() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PUT, resp -> testComplete());
  }

  @Test
  public void testSimplePOST() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.POST, resp -> testComplete());
  }

  @Test
  public void testSimpleDELETE() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.DELETE, resp -> testComplete());
  }

  @Test
  public void testSimpleHEAD() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.HEAD, resp -> testComplete());
  }

  @Test
  public void testSimpleTRACE() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.TRACE, resp -> testComplete());
  }

  @Test
  public void testSimpleCONNECT() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.CONNECT, resp -> testComplete());
  }

  @Test
  public void testSimpleOPTIONS() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.OPTIONS, resp -> testComplete());
  }

  @Test
  public void testSimplePATCH() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PATCH, resp -> testComplete());
  }

  @Test
  public void testSimpleGETAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.GET, true, resp -> testComplete());
  }

  @Test
  public void testEmptyPathGETAbsolute() {
    String uri = "";
    testSimpleRequest(uri, HttpMethod.GET, true, resp -> testComplete());
  }

  @Test
  public void testNoPathButQueryGETAbsolute() {
    String uri = "?foo=bar";
    testSimpleRequest(uri, HttpMethod.GET, true, resp -> testComplete());
  }

  @Test
  public void testSimplePUTAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PUT, true, resp -> testComplete());
  }

  @Test
  public void testSimplePOSTAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.POST, true, resp -> testComplete());
  }

  @Test
  public void testSimpleDELETEAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.DELETE, true, resp -> testComplete());
  }

  @Test
  public void testSimpleHEADAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.HEAD, true, resp -> testComplete());
  }

  @Test
  public void testSimpleTRACEAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.TRACE, true, resp -> testComplete());
  }

  @Test
  public void testSimpleCONNECTAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.CONNECT, true, resp -> testComplete());
  }

  @Test
  public void testSimpleOPTIONSAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.OPTIONS, true, resp -> testComplete());
  }

  @Test
  public void testSimplePATCHAbsolute() {
    String uri = "/some-uri?foo=bar";
    testSimpleRequest(uri, HttpMethod.PATCH, true, resp -> testComplete());
  }

  private void testSimpleRequest(String uri, HttpMethod method, Handler<HttpClientResponse> handler) {
    testSimpleRequest(uri, method, false, handler);
  }

  private void testSimpleRequest(String uri, HttpMethod method, boolean absolute, Handler<HttpClientResponse> handler) {
    boolean ssl = this instanceof Http2Test;
    HttpClientRequest req;
    if (absolute) {
      req = client.requestAbs(method, (ssl ? "https://" : "http://") + DEFAULT_HTTP_HOST + ":" + DEFAULT_HTTP_PORT + uri, handler);
    } else {
      req = client.request(method, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri, handler);
    }
    testSimpleRequest(uri, method, req, absolute);
  }

  private void testSimpleRequest(String uri, HttpMethod method, HttpClientRequest request, boolean absolute) {
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
      String expectedPath = req.method() == HttpMethod.CONNECT && req.version() == HttpVersion.HTTP_2 ? null : resource;
      String expectedQuery = req.method() == HttpMethod.CONNECT && req.version() == HttpVersion.HTTP_2 ? null : query;
      assertEquals(expectedPath, req.path());
      assertEquals(method, req.method());
      assertEquals(expectedQuery, req.query());
      req.response().end();
    });

    server.listen(onSuccess(server -> request.end()));

    await();
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
      client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testServerChainingSendFile() throws Exception {
    File file = setupFile("test-server-chaining.dat", "blah");
    server.requestHandler(req -> {
      assertTrue(req.response().sendFile(file.getAbsolutePath()) == req.response());
      assertTrue(req.response().ended());
      file.delete();
      testComplete();
    });

    server.listen(onSuccess(server -> {
      client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testResponseEndHandlers1() {
    waitFor(2);
    AtomicInteger cnt = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().headersEndHandler(v -> {
        // Insert another header
        req.response().putHeader("extraheader", "wibble");
        assertEquals(0, cnt.getAndIncrement());
      });
      req.response().bodyEndHandler(v -> {
        assertEquals(0, req.response().bytesWritten());
        assertEquals(1, cnt.getAndIncrement());
        complete();
      });
      req.response().end();
    }).listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
        assertEquals(200, res.statusCode());
        assertEquals("wibble", res.headers().get("extraheader"));
        complete();
      }).end();
    }));
    await();
  }

  @Test
  public void testResponseEndHandlers2() {
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
    }).listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
        assertEquals(200, res.statusCode());
        assertEquals("wibble", res.headers().get("extraheader"));
        res.bodyHandler(buff -> {
          assertEquals(Buffer.buffer(content), buff);
          complete();
        });
      }).end();
    }));
    await();
  }

  @Test
  public void testResponseEndHandlersChunkedResponse() {
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
    }).listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
        assertEquals(200, res.statusCode());
        assertEquals("wibble", res.headers().get("extraheader"));
        res.bodyHandler(buff -> {
          assertEquals(Buffer.buffer(content.toString()), buff);
          complete();
        });
      }).end();
    }));
    await();
  }

  @Test
  public void testResponseEndHandlersSendFile() throws Exception {
    waitFor(2);
    AtomicInteger cnt = new AtomicInteger();
    String content = "iqdioqwdqwiojqwijdwqd";
    File toSend = setupFile("somefile.txt", content);
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
      req.response().sendFile(toSend.getAbsolutePath());
    }).listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
        assertEquals(200, res.statusCode());
        assertEquals("wibble", res.headers().get("extraheader"));
        res.bodyHandler(buff -> {
          assertEquals(Buffer.buffer(content), buff);
          complete();
        });
      }).end();
    }));
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

  @Test
  public void testAbsoluteURIWithHttpSchemaInQuery() {
    testURIAndPath("http://localhost:" + DEFAULT_HTTP_PORT + "/correct/path?url=http://localhost:8008/wrong/path", "/correct/path");
  }

  @Test
  public void testRelativeURIWithHttpSchemaInQuery() {
    testURIAndPath("/correct/path?url=http://localhost:8008/wrong/path", "/correct/path");
  }

  @Test
  public void testAbsoluteURIEmptyPath() {
    testURIAndPath("http://localhost:" + DEFAULT_HTTP_PORT + "/", "/");
  }

  private void testURIAndPath(String uri, String path) {
    server.requestHandler(req -> {
      assertEquals(uri, req.uri());
      assertEquals(path, req.path());
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri, resp -> testComplete()).end();
    }));

    await();
  }

  @Test
  public void testParamUmlauteDecoding() throws UnsupportedEncodingException {
    testParamDecoding("\u00e4\u00fc\u00f6");
  }

  @Test
  public void testParamPlusDecoding() throws UnsupportedEncodingException {
    testParamDecoding("+");
  }

  @Test
  public void testParamPercentDecoding() throws UnsupportedEncodingException {
    testParamDecoding("%");
  }

  @Test
  public void testParamSpaceDecoding() throws UnsupportedEncodingException {
    testParamDecoding(" ");
  }

  @Test
  public void testParamNormalDecoding() throws UnsupportedEncodingException {
    testParamDecoding("hello");
  }

  @Test
  public void testParamAltogetherDecoding() throws UnsupportedEncodingException {
    testParamDecoding("\u00e4\u00fc\u00f6+% hello");
  }

  private void testParamDecoding(String value) throws UnsupportedEncodingException {

    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.endHandler(v -> {
        MultiMap formAttributes = req.formAttributes();
        assertEquals(value, formAttributes.get("param"));
      });
      req.response().end();
    });
    String postData = "param=" + URLEncoder.encode(value,"UTF-8");
    server.listen(onSuccess(server -> {
      client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED)
          .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(postData.length()))
          .handler(resp -> {
            testComplete();
          })
          .write(postData).end();
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "some-uri/?" + query, resp -> testComplete()).end();
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> testComplete()).end();
    }));

    await();
  }

  @Test
  public void testDefaultRequestHeaders() {
    server.requestHandler(req -> {
      if (req.version() == HttpVersion.HTTP_1_1) {
        assertEquals(1, req.headers().size());
        assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get("host"));
      } else {
        assertEquals(4, req.headers().size());
        assertEquals("https", req.headers().get(":scheme"));
        assertEquals("GET", req.headers().get(":method"));
        assertEquals("some-uri", req.headers().get(":path"));
        assertEquals("localhost:" + DEFAULT_HTTP_PORT, req.headers().get(":authority"));
      }
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> testComplete()).end();
    }));

    await();
  }

  @Test
  public void testRequestHeadersWithCharSequence() {
    HashMap<CharSequence, String> headers = new HashMap<>();
    headers.put(HttpHeaders.TEXT_HTML, "text/html");
    headers.put(HttpHeaders.USER_AGENT, "User-Agent");
    headers.put(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED, "application/x-www-form-urlencoded");

    server.requestHandler(req -> {

      assertTrue(headers.size() < req.headers().size());

      headers.forEach((k, v) -> assertEquals(v, req.headers().get(k)));
      headers.forEach((k, v) -> assertEquals(v, req.getHeader(k)));

      req.response().end();
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> testComplete());

      headers.forEach((k, v) -> req.headers().add(k, v));

      req.end();
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
      assertTrue(headers.size() < req.headers().size());
      for (Map.Entry<String, String> entry : headers) {
        assertEquals(entry.getValue(), req.headers().get(entry.getKey()));
        assertEquals(entry.getValue(), req.getHeader(entry.getKey()));
      }
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> testComplete());
      if (individually) {
        for (Map.Entry<String, String> header : headers) {
          req.headers().add(header.getKey(), header.getValue());
        }
      } else {
        req.headers().setAll(headers);
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
        req.response().headers().setAll(headers);
      }
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertTrue(headers.size() < resp.headers().size());
        for (Map.Entry<String, String> entry : headers) {
          assertEquals(entry.getValue(), resp.headers().get(entry.getKey()));
          assertEquals(entry.getValue(), resp.getHeader(entry.getKey()));
        }
        testComplete();
      }).end();
    }));

    await();
  }

  @Test
  public void testResponseHeadersWithCharSequence() {
    HashMap<CharSequence, String> headers = new HashMap<>();
    headers.put(HttpHeaders.TEXT_HTML, "text/html");
    headers.put(HttpHeaders.USER_AGENT, "User-Agent");
    headers.put(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED, "application/x-www-form-urlencoded");

    server.requestHandler(req -> {
      headers.forEach((k, v) -> req.response().headers().add(k, v));
      req.response().end();
    });

    server.listen(onSuccess(server -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertTrue(headers.size() < resp.headers().size());

        headers.forEach((k,v) -> assertEquals(v, resp.headers().get(k)));
        headers.forEach((k,v) -> assertEquals(v, resp.getHeader(k)));

        testComplete();
      }).end();
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertEquals(cookies.size(), resp.cookies().size());
          for (int i = 0; i < cookies.size(); ++i) {
            assertEquals(cookies.get(i), resp.cookies().get(i));
          }
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testUseRequestAfterComplete() {
    server.requestHandler(noOpHandler());

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
      req.end();

      Buffer buff = Buffer.buffer();
      assertIllegalStateException(() -> req.end());
      assertIllegalStateException(() -> req.continueHandler(noOpHandler()));
      assertIllegalStateException(() -> req.drainHandler(noOpHandler()));
      assertIllegalStateException(() -> req.end("foo"));
      assertIllegalStateException(() -> req.end(buff));
      assertIllegalStateException(() -> req.end("foo", "UTF-8"));
      assertIllegalStateException(() -> req.sendHead());
      assertIllegalStateException(() -> req.setChunked(false));
      assertIllegalStateException(() -> req.setWriteQueueMaxSize(123));
      assertIllegalStateException(() -> req.write(buff));
      assertIllegalStateException(() -> req.write("foo"));
      assertIllegalStateException(() -> req.write("foo", "UTF-8"));
      assertIllegalStateException(() -> req.write(buff));
      assertIllegalStateException(() -> req.writeQueueFull());

      testComplete();
    }));

    await();
  }

  @Test
  public void testRequestBodyBufferAtEnd() {
    Buffer body = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> req.bodyHandler(buffer -> {
      assertEquals(body, buffer);
      req.response().end();
    }));

    server.listen(onSuccess(server -> {
      client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> testComplete()).end(body);
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

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
      if (encoding == null) {
        req.end(body);
      } else {
        req.end(body, encoding);
      }
    }));

    await();
  }

  @Test
  public void testRequestBodyWriteChunked() {
    testRequestBodyWrite(true);
  }

  @Test
  public void testRequestBodyWriteNonChunked() {
    testRequestBodyWrite(false);
  }

  private void testRequestBodyWrite(boolean chunked) {
    Buffer body = Buffer.buffer();

    server.requestHandler(req -> {
      req.bodyHandler(buffer -> {
        assertEquals(body, buffer);
        req.response().end();
      });
    });

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> testComplete());
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

    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());

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
  public void testRequestWrite() {
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
    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
      req.setChunked(true);
      int padding = 5;
      for (int i = 0;i < times;i++) {
        Buffer paddedChunk = TestUtils.leftPad(padding, chunk);
        assertEquals(paddedChunk.getByteBuf().readerIndex(), padding);
        req.write(paddedChunk);
      }
      req.end();
    }));

    await();
  }

  @Test
  public void testConnectWithoutResponseHandler() throws Exception {
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).end();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).end("whatever");
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).end("whatever", "UTF-8");
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).end(Buffer.buffer("whatever"));
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).sendHead();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).write(Buffer.buffer("whatever"));
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).write("whatever");
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).write("whatever", "UTF-8");
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testClientExceptionHandlerCalledWhenFailingToConnect() throws Exception {
    client.request(HttpMethod.GET, 9998, "255.255.255.255", DEFAULT_TEST_URI, resp -> fail("Connect should not be called")).
        exceptionHandler(error -> testComplete()).
        endHandler(done -> fail()).
        end();
    await();
  }

  @Test
  public void testClientExceptionHandlerCalledWhenServerTerminatesConnection() throws Exception {
    int numReqs = 10;
    CountDownLatch latch = new CountDownLatch(numReqs);
    server.requestHandler(request -> {
      request.response().close();
    }).listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      // Exception handler should be called for any requests in the pipeline if connection is closed
      for (int i = 0; i < numReqs; i++) {
        client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> fail("Connect should not be called")).
            exceptionHandler(error -> latch.countDown()).endHandler(done -> fail()).end();
      }
    }));
    awaitLatch(latch);
  }

  @Test
  public void testClientExceptionHandlerCalledWhenServerTerminatesConnectionAfterPartialResponse() throws Exception {
    server.requestHandler(request -> {
      //Write partial response then close connection before completing it
      request.response().setChunked(true).write("foo").close();
    }).listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      // Exception handler should be called for any requests in the pipeline if connection is closed
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp ->
          resp.exceptionHandler(t -> testComplete())).exceptionHandler(error -> fail()).end();
    }));
    await();
  }

  @Test
  public void testClientExceptionHandlerCalledWhenExceptionOnDataHandler() throws Exception {
    server.requestHandler(request -> {
      request.response().end("foo");
    }).listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      // Exception handler should be called for any exceptions in the data handler
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.handler(data -> {
          throw new RuntimeException("should be caught");
        });
        resp.exceptionHandler(t -> testComplete());
      }).exceptionHandler(error -> fail()).end();
    }));
    await();
  }

  @Test
  public void testClientExceptionHandlerCalledWhenExceptionOnBodyHandler() throws Exception {
    server.requestHandler(request -> {
      request.response().end("foo");
    }).listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      // Exception handler should be called for any exceptions in the data handler
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(data -> {
          throw new RuntimeException("should be caught");
        });
        resp.exceptionHandler(t -> testComplete());
      }).exceptionHandler(error -> fail()).end();
    }));
    await();
  }

  @Test
  public void testNoExceptionHandlerCalledWhenResponseReceivedOK() throws Exception {
    server.requestHandler(request -> {
      request.response().end();
    }).listen(DEFAULT_HTTP_PORT, onSuccess(s -> {
      client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          vertx.setTimer(100, tid -> testComplete());
        });
        resp.exceptionHandler(t -> {
          fail("Should not be called");
        });
      }).exceptionHandler(t -> {
        fail("Should not be called");
      }).end();
    }));
    await();
  }

  @Test
  public void testServerExceptionHandlerOnClose() {
    vertx.createHttpServer().requestHandler(req -> {
      HttpServerResponse resp = req.response();
      AtomicInteger reqExceptionHandlerCount = new AtomicInteger();
      AtomicInteger respExceptionHandlerCount = new AtomicInteger();
      AtomicInteger respEndHandlerCount = new AtomicInteger();
      req.exceptionHandler(err -> {
        assertEquals(1, reqExceptionHandlerCount.incrementAndGet());
        assertEquals(0, respExceptionHandlerCount.get());
        assertEquals(0, respEndHandlerCount.get());
        assertTrue(resp.closed());
        assertFalse(resp.ended());
        try {
          resp.end();
        } catch (IllegalStateException ignore) {
          // Expected
        }
      });
      resp.exceptionHandler(err -> {
        assertEquals(1, reqExceptionHandlerCount.get());
        assertEquals(1, respExceptionHandlerCount.incrementAndGet());
        assertEquals(0, respEndHandlerCount.get());
      });
      resp.endHandler(v -> {
        assertEquals(1, reqExceptionHandlerCount.get());
        assertEquals(1, respExceptionHandlerCount.get());
        assertEquals(1, respEndHandlerCount.incrementAndGet());
      });
      req.connection().closeHandler(v -> {
        assertEquals(1, reqExceptionHandlerCount.get());
        assertEquals(1, respExceptionHandlerCount.get());
        assertEquals(1, respEndHandlerCount.get());
        testComplete();
      });
    }).listen(DEFAULT_HTTP_PORT, ar -> {
      HttpClient client = vertx.createHttpClient();
      HttpClientRequest req = client.put(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somerui", handler -> {

      }).setChunked(true);
      req.sendHead(v -> {
        req.connection().close();
      });
    });
    await();
  }

  @Test
  public void testClientRequestExceptionHandlerCalledWhenConnectionClosed() throws Exception {
    server.requestHandler(req -> {
      req.handler(buff -> {
        req.connection().close();
      });
    });
    startServer();
    HttpClientRequest req = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      resp.handler(chunk -> {
        resp.request().connection().close();
      });
    }).setChunked(true);
    req.exceptionHandler(err -> {
      testComplete();
    });
    req.write("chunk");
    await();
  }

  @Test
  public void testClientResponseExceptionHandlerCalledWhenConnectionClosed() throws Exception {
    AtomicReference<HttpConnection> conn = new AtomicReference<>();
    server.requestHandler(req -> {
      conn.set(req.connection());
      req.response().setChunked(true).write("chunk");
    });
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      resp.handler(buff -> {
        conn.get().close();
      });
      resp.exceptionHandler(err -> {
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDefaultStatus() {
    testStatusCode(-1, null);
  }

  @Test
  public void testDefaultOther() {
    // Doesn't really matter which one we choose
    testStatusCode(405, null);
  }

  @Test
  public void testOverrideStatusMessage() {
    testStatusCode(404, "some message");
  }

  @Test
  public void testOverrideDefaultStatusMessage() {
    testStatusCode(-1, "some other message");
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        int theCode;
        if (code == -1) {
          // Default code - 200
          assertEquals(200, resp.statusCode());
          theCode = 200;
        } else {
          theCode = code;
        }
        if (statusMessage != null && resp.version() != HttpVersion.HTTP_2) {
          assertEquals(statusMessage, resp.statusMessage());
        } else {
          assertEquals(HttpResponseStatus.valueOf(theCode).reasonPhrase(), resp.statusMessage());
        }
        testComplete();
      }).end();
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
        req.response().trailers().setAll(trailers);
      }
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertEquals(trailers.size(), resp.trailers().size());
          for (Map.Entry<String, String> entry : trailers) {
            assertEquals(entry.getValue(), resp.trailers().get(entry.getKey()));
            assertEquals(entry.getValue(), resp.getTrailer(entry.getKey()));
          }
          testComplete();
        });
      }).end();
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.endHandler(v -> {
          assertTrue(resp.trailers().isEmpty());
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testUseResponseAfterComplete() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      assertFalse(resp.ended());
      resp.end();
      assertTrue(resp.ended());
      checkHttpServerResponse(resp);
      testComplete();
    });
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
    await();
  }

  private void checkHttpServerResponse(HttpServerResponse resp) {
    Buffer buff = Buffer.buffer();
    assertIllegalStateException(() -> resp.drainHandler(noOpHandler()));
    assertIllegalStateException(() -> resp.end());
    assertIllegalStateException(() -> resp.end("foo"));
    assertIllegalStateException(() -> resp.end(buff));
    assertIllegalStateException(() -> resp.end("foo", "UTF-8"));
    assertIllegalStateException(() -> resp.exceptionHandler(noOpHandler()));
    assertIllegalStateException(() -> resp.setChunked(false));
    assertIllegalStateException(() -> resp.setWriteQueueMaxSize(123));
    assertIllegalStateException(() -> resp.write(buff));
    assertIllegalStateException(() -> resp.write("foo"));
    assertIllegalStateException(() -> resp.write("foo", "UTF-8"));
    assertIllegalStateException(() -> resp.write(buff));
    assertIllegalStateException(() -> resp.writeQueueFull());
    assertIllegalStateException(() -> resp.sendFile("asokdasokd"));
  }

  @Test
  public void testResponseBodyBufferAtEnd() {
    Buffer body = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().end(body);
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(body, buff);
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testResponseBodyWriteChunked() {
    testResponseBodyWrite(true);
  }

  @Test
  public void testResponseBodyWriteNonChunked() {
    testResponseBodyWrite(false);
  }

  private void testResponseBodyWrite(boolean chunked) {
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

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(body, buff);
          testComplete();
        });
      }).end();
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

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(bodyBuff, buff);
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testResponseWrite() {
    Buffer body = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(body, buff);
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testSendFile() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, false);
  }

  @Test
  public void testSendFileWithHandler() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, true);
  }

  private void sendFile(String fileName, String contentExpected, boolean handler) throws Exception {
    File fileToSend = setupFile(fileName, contentExpected);

    CountDownLatch latch;
    if (handler) {
      latch = new CountDownLatch(2);
    } else {
      latch = new CountDownLatch(1);
    }

    server.requestHandler(req -> {
      if (handler) {
        Handler<AsyncResult<Void>> completionHandler = onSuccess(v -> latch.countDown());
        req.response().sendFile(fileToSend.getAbsolutePath(), completionHandler);
      } else {
        req.response().sendFile(fileToSend.getAbsolutePath());
      }
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(200, resp.statusCode());

        assertEquals("text/html", resp.headers().get("Content-Type"));
        resp.bodyHandler(buff -> {
          assertEquals(contentExpected, buff.toString());
          assertEquals(fileToSend.length(), Long.parseLong(resp.headers().get("content-length")));
          latch.countDown();
        });
      }).end();
    }));

    assertTrue("Timed out waiting for test to complete.", latch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testSendNonExistingFile() throws Exception {
    server.requestHandler(req -> {
      final Context ctx = vertx.getOrCreateContext();
      req.response().sendFile("/not/existing/path", event -> {
        assertEquals(ctx, vertx.getOrCreateContext());
        if (event.failed()) {
          req.response().end("failed");
        }
      });
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(buff -> {
          assertEquals("failed", buff.toString());
          testComplete();
        });
      }).end();
    }));

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

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(file.length(), Long.parseLong(resp.headers().get("content-length")));
        assertEquals("wibble", resp.headers().get("content-type"));
        resp.bodyHandler(buff -> {
          assertEquals(content, buff.toString());
          file.delete();
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testSendFileNotFound() throws Exception {

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile("nosuchfile.html");
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        fail("Should not receive response");
      }).end();
      vertx.setTimer(100, tid -> testComplete());
    }));

    await();
  }

  @Test
  public void testSendFileNotFoundWithHandler() throws Exception {

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile("nosuchfile.html", onFailure(t -> {
        assertTrue(t instanceof FileNotFoundException);
        testComplete();
      }));
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        fail("Should not receive response");
      }).end();
    }));

    await();
  }

  @Test
  public void testSendFileDirectoryWithHandler() throws Exception {

    File dir = testFolder.newFolder();

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(dir.getAbsolutePath(), onFailure(t -> {
        assertTrue(t instanceof FileNotFoundException);
        testComplete();
      }));
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        fail("Should not receive response");
      }).end();
    }));

    await();
  }

  @Test
  public void testSendOpenRangeFileFromClasspath() {
    vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(res -> {
      res.response().sendFile("webroot/somefile.html", 6);
    }).listen(onSuccess(res -> {
      vertx.createHttpClient(new HttpClientOptions()).request(HttpMethod.GET, 8080, "localhost", "/", resp -> {
        resp.bodyHandler(buff -> {
          assertTrue(buff.toString().startsWith("<body>blah</body></html>"));
          testComplete();
        });
      }).end();
    }));
    await();
  }

  @Test
  public void testSendRangeFileFromClasspath() {
    vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(res -> {
      res.response().sendFile("webroot/somefile.html", 6, 6);
    }).listen(onSuccess(res -> {
      vertx.createHttpClient(new HttpClientOptions()).request(HttpMethod.GET, 8080, "localhost", "/", resp -> {
        resp.bodyHandler(buff -> {
          assertEquals("<body>", buff.toString());
          testComplete();
        });
      }).end();
    }));
    await();
  }

  @Test
  public void test100ContinueHandledAutomatically() throws Exception {
    Buffer toSend = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(data -> {
        assertEquals(toSend, data);
        req.response().end();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
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
  public void test100ContinueHandledManually() throws Exception {

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions());

    Buffer toSend = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> {
      assertEquals("100-continue", req.getHeader("expect"));
      req.response().writeContinue();
      req.bodyHandler(data -> {
        assertEquals(toSend, data);
        req.response().end();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
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
  public void test100ContinueRejectedManually() throws Exception {

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions());

    server.requestHandler(req -> {
      req.response().setStatusCode(405).end();
      req.bodyHandler(data -> {
        fail("body should not be received");
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(405, resp.statusCode());
        testComplete();
      });
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
  public void testClientDrainHandler() {
    pausingServer(resumeFuture -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
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
    });

    await();
  }

  @Test
  public void testClientRequestExceptionHandlerCalledWhenExceptionOnDrainHandler() {
    pausingServer(resumeFuture -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
      req.setChunked(true);
      assertFalse(req.writeQueueFull());
      req.setWriteQueueMaxSize(1000);
      Buffer buff = TestUtils.randomBuffer(10000);
      AtomicBoolean failed = new AtomicBoolean();
      vertx.setPeriodic(1, id -> {
        req.write(buff);
        if (req.writeQueueFull()) {
          vertx.cancelTimer(id);
          req.drainHandler(v -> {
            throw new RuntimeException("error");
          })
              .exceptionHandler(t -> {
                // Called a second times when testComplete is called and close the http client
                if (failed.compareAndSet(false, true)) {
                  testComplete();
                }
              });

          // Tell the server to resume
          resumeFuture.complete();
        }
      });
    });

    await();
  }

  private void pausingServer(Consumer<Future<Void>> consumer) {
    Future<Void> resumeFuture = Future.future();
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.pause();
      Context ctx = vertx.getOrCreateContext();
      resumeFuture.setHandler(v1 -> {
        ctx.runOnContext(v2 -> {
          req.resume();
        });
      });
      req.handler(buff -> {
        req.response().write(buff);
      });
    });

    server.listen(onSuccess(s -> consumer.accept(resumeFuture)));
  }

  @Test
  public void testServerDrainHandler() {
    drainingServer(resumeFuture -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.pause();
        resumeFuture.setHandler(ar -> resp.resume());
      }).end();
    });

    await();
  }

  private void drainingServer(Consumer<Future<Void>> consumer) {

    Future<Void> resumeFuture = Future.future();

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

    server.listen(onSuccess(s -> consumer.accept(resumeFuture)));
  }

  @Test
  public void testConnectionErrorsGetReportedToRequest() throws InterruptedException {
    AtomicInteger req1Exceptions = new AtomicInteger();
    AtomicInteger req2Exceptions = new AtomicInteger();
    AtomicInteger req3Exceptions = new AtomicInteger();

    CountDownLatch latch = new CountDownLatch(3);

    // This one should cause an error in the Client Exception handler, because it has no exception handler set specifically.
    HttpClientRequest req1 = client.request(HttpMethod.GET, 9998, DEFAULT_HTTP_HOST, "someurl1", resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });

    req1.exceptionHandler(t -> {
      assertEquals("More than one call to req1 exception handler was not expected", 1, req1Exceptions.incrementAndGet());
      latch.countDown();
    });

    HttpClientRequest req2 = client.request(HttpMethod.GET, 9998, DEFAULT_HTTP_HOST, "someurl2", resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });

    req2.exceptionHandler(t -> {
      assertEquals("More than one call to req2 exception handler was not expected", 1, req2Exceptions.incrementAndGet());
      latch.countDown();
    });

    HttpClientRequest req3 = client.request(HttpMethod.GET, 9998, DEFAULT_HTTP_HOST, "someurl2", resp -> {
      fail("Should never get a response on a bad port, if you see this message than you are running an http server on port 9998");
    });

    req3.exceptionHandler(t -> {
      assertEquals("More than one call to req2 exception handler was not expected", 1, req3Exceptions.incrementAndGet());
      latch.countDown();
    });

    req1.end();
    req2.end();
    req3.end();

    awaitLatch(latch);
    testComplete();
  }

  @Test
  public void testRequestTimesoutWhenIndicatedPeriodExpiresWithoutAResponseFromRemoteServer() {
    server.requestHandler(noOpHandler()); // No response handler so timeout triggers
    AtomicBoolean failed = new AtomicBoolean();
    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        fail("End should not be called because the request should timeout");
      });
      req.exceptionHandler(t -> {
        // Catch the first, the second is going to be a connection closed exception when the
        // server is shutdown on testComplete
        if (failed.compareAndSet(false, true)) {
          assertTrue("Expected to end with timeout exception but ended with other exception: " + t, t instanceof TimeoutException);
          testComplete();
        }
      });
      req.setTimeout(1000);
      req.end();
    }));

    await();
  }

  @Test
  public void testRequestTimeoutCanceledWhenRequestHasAnOtherError() {
    AtomicReference<Throwable> exception = new AtomicReference<>();
    // There is no server running, should fail to connect
    HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
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
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
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
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> fail("Response should not be handled"));
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
  public void testConnectInvalidPort() {
    client.request(HttpMethod.GET, 9998, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> fail("Connect should not be called")).
        exceptionHandler(t -> testComplete()).
        end();

    await();
  }

  @Test
  public void testConnectInvalidHost() {
    client.request(HttpMethod.GET, 9998, "255.255.255.255", DEFAULT_TEST_URI, resp -> fail("Connect should not be called")).
        exceptionHandler(t -> testComplete()).
        end();

    await();
  }

  @Test
  public void testSetHandlersAfterListening() throws Exception {
    server.requestHandler(noOpHandler());

    server.listen(onSuccess(s -> {
      assertIllegalStateException(() -> server.requestHandler(noOpHandler()));
      assertIllegalStateException(() -> server.websocketHandler(noOpHandler()));
      testComplete();
    }));

    await();
  }

  @Test
  public void testSetHandlersAfterListening2() throws Exception {
    server.requestHandler(noOpHandler());

    server.listen(onSuccess(v -> testComplete()));
    assertIllegalStateException(() -> server.requestHandler(noOpHandler()));
    assertIllegalStateException(() -> server.websocketHandler(noOpHandler()));
    await();
  }

  @Test
  public void testListenNoHandlers() throws Exception {
    assertIllegalStateException(() -> server.listen(ar -> {
    }));
  }

  @Test
  public void testListenNoHandlers2() throws Exception {
    assertIllegalStateException(() -> server.listen());
  }

  @Test
  public void testListenTwice() throws Exception {
    server.requestHandler(noOpHandler());
    server.listen(onSuccess(v -> testComplete()));
    assertIllegalStateException(() -> server.listen());
    await();
  }

  @Test
  public void testListenTwice2() throws Exception {
    server.requestHandler(noOpHandler());
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      assertIllegalStateException(() -> server.listen());
      testComplete();
    });
    await();
  }

  @Test
  public void testHeadNoBody() {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.HEAD, req.method());
      // Head never contains a body but it can contain a Content-Length header
      // Since headers from HEAD must correspond EXACTLY with corresponding headers for GET
      req.response().headers().set("Content-Length", String.valueOf(41));
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.HEAD, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(41, Integer.parseInt(resp.headers().get("Content-Length")));
        resp.endHandler(v -> testComplete());
      }).end();
    }));

    await();
  }

  @Test
  public void testRemoteAddress() {
    server.requestHandler(req -> {
      assertEquals("127.0.0.1", req.remoteAddress().host());
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> resp.endHandler(v -> testComplete())).end();
    }));

    await();
  }

  @Test
  public void testGetAbsoluteURI() {
    server.requestHandler(req -> {
      assertEquals(req.scheme() + "://localhost:" + DEFAULT_HTTP_PORT + "/foo/bar", req.absoluteURI());
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/foo/bar", resp -> resp.endHandler(v -> testComplete())).end();
    }));

    await();
  }

  @Test
  public void testListenInvalidPort() throws Exception {
    /* Port 7 is free for use by any application in Windows, so this test fails. */
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(7));
    server.requestHandler(noOpHandler()).listen(onFailure(server -> testComplete()));
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
        req.response().write(TestUtils.randomBuffer(numBytes));
      }
      req.response().end();
    });

    AtomicBoolean paused = new AtomicBoolean();
    Buffer totBuff = Buffer.buffer();
    HttpClientRequest clientRequest = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
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
    });

    server.listen(onSuccess(s -> clientRequest.end()));

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
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setKeepAlive(true));
    for (int i = 0;i < num;i++) {
      int idx = i;
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/" + i, resp -> {
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
      }).end();
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
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setKeepAlive(true));
    for (int i = 0;i < num;i++) {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(data, buff);
          complete();
        });
        resp.pause();
        vertx.setTimer(10, id -> {
          resp.resume();
        });
      }).end();
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
    startServer();
    HttpClientRequest req = client.put(DEFAULT_HTTP_PORT, DEFAULT_HTTPS_HOST, DEFAULT_TEST_URI, resp -> {
      resp.endHandler(v -> {
        testComplete();
      });
    }).exceptionHandler(this::fail)
      .setChunked(true);
    while (!req.writeQueueFull()) {
      Buffer buff = Buffer.buffer(TestUtils.randomAlphaString(1024));
      expected.appendBuffer(buff);
      req.write(buff);
    }
    resumeCF.complete(null);
    req.end();
    await();
  }

  @Test
  public void testPausedHttpServerRequestDuringLastChunkEndsTheRequest() throws Exception {
    server.requestHandler(req -> {
      req.handler(buff -> {
        assertEquals("small", buff.toString());
        req.pause();
      });
      req.endHandler(v -> {
        req.response().end();
      });
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1));
    client.put(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri", resp -> {
      complete();
    }).end("small");
    await();
  }

  @Test
  public void testFormUploadSmallFile() throws Exception {
    testFormUploadFile(TestUtils.randomAlphaString(100), false);
  }

  @Test
  public void testFormUploadLargerFile() throws Exception {
    testFormUploadFile(TestUtils.randomAlphaString(20000), false);
  }

  @Test
  public void testFormUploadSmallFileStreamToDisk() throws Exception {
    testFormUploadFile(TestUtils.randomAlphaString(100), true);
  }

  @Test
  public void testFormUploadLargerFileStreamToDisk() throws Exception {
    testFormUploadFile(TestUtils.randomAlphaString(20000), true);
  }

  private void testFormUploadFile(String contentStr, boolean streamToDisk) throws Exception {

    waitFor(2);

    Buffer content = Buffer.buffer(contentStr);

    AtomicInteger attributeCount = new AtomicInteger();

    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.setExpectMultipart(true);
        assertTrue(req.isExpectMultipart());

        // Now try setting again, it shouldn't have an effect
        req.setExpectMultipart(true);
        assertTrue(req.isExpectMultipart());

        req.uploadHandler(upload -> {
          Buffer tot = Buffer.buffer();
          assertEquals("file", upload.name());
          assertEquals("tmp-0.txt", upload.filename());
          assertEquals("image/gif", upload.contentType());
          String uploadedFileName;
          if (!streamToDisk) {
            upload.handler(tot::appendBuffer);
            uploadedFileName = null;
          } else {
            uploadedFileName = new File(testDir, UUID.randomUUID().toString()).getPath();
            upload.streamToFileSystem(uploadedFileName);
          }
          upload.endHandler(v -> {
            if (streamToDisk) {
              Buffer uploaded = vertx.fileSystem().readFileBlocking(uploadedFileName);
              assertEquals(content.length(), uploaded.length());
              assertEquals(content, uploaded);
            } else {
              assertEquals(content, tot);
            }
            assertTrue(upload.isSizeAvailable());
            assertEquals(content.length(), upload.size());
            complete();
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
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/form", resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(0, attributeCount.get());
        complete();
      });

      String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
      Buffer buffer = Buffer.buffer();
      String body =
          "--" + boundary + "\r\n" +
              "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
              "Content-Type: image/gif\r\n" +
              "\r\n" +
              contentStr + "\r\n" +
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
      if (req.method() == HttpMethod.POST) {
        assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.setExpectMultipart(true);
        req.uploadHandler(upload -> upload.handler(buffer -> {
          fail("Should get here");
        }));
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          assertEquals("vert x", attrs.get("framework"));
          assertEquals("vert x", req.getFormAttribute("framework"));
          assertEquals("jvm", attrs.get("runson"));
          assertEquals("jvm", req.getFormAttribute("runson"));
          req.response().end();
        });
      }
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/form", resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(2, attributeCount.get());
        testComplete();
      });
      try {
        Buffer buffer = Buffer.buffer();
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
      if (req.method() == HttpMethod.POST) {
        assertEquals(req.path(), "/form");
        req.setExpectMultipart(true);
        req.uploadHandler(event -> event.handler(buffer -> {
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
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/form", resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(3, attributeCount.get());
        testComplete();
      });
      Buffer buffer = Buffer.buffer();
      buffer.appendString("origin=junit-testUserAlias&login=admin%40foo.bar&pass+word=admin");
      req.headers().set("content-length", String.valueOf(buffer.length()));
      req.headers().set("content-type", "application/x-www-form-urlencoded");
      req.write(buffer).end();
    }));

    await();
  }

  @Test
  public void testHostHeaderOverridePossible() {
    server.requestHandler(req -> {
      assertEquals("localhost:4444", req.host());
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> testComplete());
      req.setHost("localhost:4444");
      req.end();
    }));

    await();
  }

  @Test
  public void testResponseBodyWriteFixedString() {
    String body = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
    Buffer bodyBuff = Buffer.buffer(body);

    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().write(body);
      req.response().end();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        resp.bodyHandler(buff -> {
          assertEquals(bodyBuff, buff);
          testComplete();
        });
      }).end();
    }));

    await();
  }

  @Test
  public void testResponseDataTimeout() {
    Buffer expected = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> {
      req.response().setChunked(true).write(expected);
    });
    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI);
      Buffer received = Buffer.buffer();
      req.handler(resp -> {
        req.setTimeout(500);
        resp.handler(received::appendBuffer);
      });
      AtomicInteger count = new AtomicInteger();
      req.exceptionHandler(t -> {
        if (count.getAndIncrement() == 0) {
          assertTrue(t instanceof TimeoutException);
          assertEquals(expected, received);
          testComplete();
        }
      });
      req.sendHead();
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
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      for (int i = 0; i < numThreads; i++) {
        int index = i;
        threads[i] = new Thread() {
          public void run() {
            client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
              assertEquals(200, res.statusCode());
              assertEquals(String.valueOf(index), res.headers().get("count"));
              latch.countDown();
            }).putHeader("count", String.valueOf(index)).end();
          }
        };
        threads[i].start();
      }
    });
    awaitLatch(latch);
    for (int i = 0; i < numThreads; i++) {
      threads[i].join();
    }
  }

  @Test
  public void testInVerticle() throws Exception {
    testInVerticle(false);
  }

  private void testInVerticle(boolean worker) throws Exception {
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
          assertSame(ctx, Vertx.currentContext());
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
        });
        server.listen(ar -> {
          assertTrue(ar.succeeded());
          assertSame(ctx, Vertx.currentContext());
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          client = vertx.createHttpClient(new HttpClientOptions());
          client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", res -> {
            assertSame(ctx, Vertx.currentContext());
            if (!worker) {
              assertSame(thr, Thread.currentThread());
            }
            assertEquals(200, res.statusCode());
            testComplete();
          }).end();
        });
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(worker));
    await();
  }

  @Test
  public void testUseInMultithreadedWorker() throws Exception {
    class MyVerticle extends AbstractVerticle {
      @Override
      public void start() {
        assertIllegalStateException(() -> server = vertx.createHttpServer(new HttpServerOptions()));
        assertIllegalStateException(() -> client = vertx.createHttpClient(new HttpClientOptions()));
        testComplete();
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(true).setMultiThreaded(true));
    await();
  }

  @Test
  public void testMultipleServerClose() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    AtomicInteger times = new AtomicInteger();
    // We assume the endHandler and the close completion handler are invoked in the same context task
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    server.requestStream().endHandler(v -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      times.incrementAndGet();
    });
    server.close(ar1 -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      server.close(ar2 -> {
        server.close(ar3 -> {
          assertEquals(1, times.get());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testClearHandlersOnEnd() {
    String path = "/some/path";
    server = vertx.createHttpServer(createBaseServerOptions());
    server.requestHandler(req -> {
      req.endHandler(v -> {
        try {
          req.endHandler(null);
          req.exceptionHandler(null);
          req.handler(null);
          req.bodyHandler(null);
          req.uploadHandler(null);
        } catch (Exception e) {
          fail("Was expecting to set to null the handlers when the request is completed");
          return;
        }
        HttpServerResponse resp = req.response();
        resp.setStatusCode(200).end();
        try {
          resp.endHandler(null);
          resp.exceptionHandler(null);
          resp.drainHandler(null);
          resp.bodyEndHandler(null);
          resp.closeHandler(null);
          resp.headersEndHandler(null);
        } catch (Exception e) {
          fail("Was expecting to set to null the handlers when the response is completed");
        }
      });
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      HttpClientRequest req = client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path);
      AtomicInteger count = new AtomicInteger();
      req.handler(resp -> {
        resp.endHandler(v -> {
          try {
            resp.endHandler(null);
            resp.exceptionHandler(null);
            resp.handler(null);
            resp.bodyHandler(null);
          } catch (Exception e) {
            fail("Was expecting to set to null the handlers when the response is completed");
            return;
          }
          if (count.incrementAndGet() == 2) {
            testComplete();
          }
        });
      });
      req.endHandler(done -> {
        try {
          req.handler(null);
          req.exceptionHandler(null);
          req.endHandler(null);
          req.drainHandler(null);
          req.connectionHandler(null);
          req.continueHandler(null);
        } catch (Exception e) {
          fail("Was expecting to set to null the handlers when the response is completed");
          return;
        }
        if (count.incrementAndGet() == 2) {
          testComplete();
        }
      });
      req.end();

    });
    await();
  }

  @Test
  public void testSetHandlersOnEnd() throws Exception {
    String path = "/some/path";
    server.requestHandler(req -> req.response().setStatusCode(200).end());
    startServer();
    HttpClientRequest req = client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path);
    req.handler(resp -> {
    });
    req.endHandler(done -> {
      try {
        req.handler(arg -> {
        });
        fail();
      } catch (Exception ignore) {
      }
      try {
        req.exceptionHandler(arg -> {
        });
        fail();
      } catch (Exception ignore) {
      }
      try {
        req.endHandler(arg -> {
        });
        fail();
      } catch (Exception ignore) {
      }
      testComplete();
    });
    req.end();
    await();
  }

  @Test
  public void testRequestEnded() {
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
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.getNow(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/blah", resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testRequestEndedNoEndHandler() {
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
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.getNow(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/blah", resp -> {
        assertEquals(200, resp.statusCode());
      });
    });
    await();
  }

  @Test
  public void testInMultithreadedWorker() throws Exception {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        assertTrue(Vertx.currentContext().isWorkerContext());
        assertTrue(Vertx.currentContext().isMultiThreadedWorkerContext());
        assertTrue(Context.isOnWorkerThread());
        try {
          vertx.createHttpServer();
          fail("Should throw exception");
        } catch (IllegalStateException e) {
          // OK
        }
        try {
          vertx.createHttpClient();
          fail("Should throw exception");
        } catch (IllegalStateException e) {
          // OK
        }
        testComplete();
      }
    }, new DeploymentOptions().setWorker(true).setMultiThreaded(true));
    await();
  }

  @Test
  public void testAbsoluteURIServer() {
    server.close();
    // Listen on all addresses
    server = vertx.createHttpServer(createBaseServerOptions().setHost("0.0.0.0"));
    server.requestHandler(req -> {
      String absURI = req.absoluteURI();
      assertEquals(req.scheme() + "://localhost:8080/path", absURI);
      req.response().end();
    });
    server.listen(onSuccess(s -> {
      String host = "localhost";
      String path = "/path";
      int port = 8080;
      client.getNow(port, host, path, resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      });
    }));

    await();
  }

  @Test
  public void testDumpManyRequestsOnQueue() throws Exception {
    int sendRequests = 10000;
    AtomicInteger receivedRequests = new AtomicInteger();
    vertx.createHttpServer(createBaseServerOptions()).requestHandler(r-> {
      r.response().end();
      if (receivedRequests.incrementAndGet() == sendRequests) {
        testComplete();
      }
    }).listen(onSuccess(s -> {
      HttpClientOptions ops = createBaseClientOptions()
          .setDefaultPort(DEFAULT_HTTP_PORT)
          .setPipelining(true)
          .setKeepAlive(true);
      HttpClient client = vertx.createHttpClient(ops);
      IntStream.range(0, sendRequests).forEach(x -> client.getNow("/", r -> {}));
    }));
    await();
  }

  @Test
  public void testOtherMethodWithRawMethod() throws Exception {
    try {
      client.request(HttpMethod.OTHER, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      }).end();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testOtherMethodRequest() throws Exception {
    server.requestHandler(r -> {
      assertEquals(HttpMethod.OTHER, r.method());
      assertEquals("COPY", r.rawMethod());
      r.response().end();
    }).listen(onSuccess(s -> {
      client.request(HttpMethod.OTHER, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
        testComplete();
      }).setRawMethod("COPY").end();
    }));
    await();
  }

  @Test
  public void testClientConnectionHandler() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(onSuccess(s -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    AtomicInteger status = new AtomicInteger();
    HttpClientRequest req = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(1, status.getAndIncrement());
      testComplete();
    });
    req.connectionHandler(conn -> {
      assertEquals(0, status.getAndIncrement());
    });
    req.end();
    await();
  }

  @Test
  public void testServerConnectionHandler() throws Exception {
    AtomicInteger status = new AtomicInteger();
    AtomicReference<HttpConnection> connRef = new AtomicReference<>();
    server.connectionHandler(conn -> {
      assertEquals(0, status.getAndIncrement());
      assertNull(connRef.getAndSet(conn));
    });
    server.requestHandler(req -> {
      assertEquals(1, status.getAndIncrement());
      assertSame(connRef.get(), req.connection());
      req.response().end();
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(onSuccess(s -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      testComplete();
    });
    await();
  }

  @Test
  public void testClientConnectionClose() throws Exception {
    // Test client connection close + server close handler
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      AtomicInteger len = new AtomicInteger();
      req.handler(buff -> {
        if (len.addAndGet(buff.length()) == 1024) {
          latch.countDown();
        }
      });
      req.connection().closeHandler(v -> {
        testComplete();
      });
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(onSuccess(s -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    HttpClientRequest req = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      fail();
    });
    req.setChunked(true);
    req.write(TestUtils.randomBuffer(1024));
    awaitLatch(latch);
    req.connection().close();
    await();
  }

  @Test
  public void testServerConnectionClose() throws Exception {
    // Test server connection close + client close handler
    server.requestHandler(req -> {
      req.connection().close();
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(onSuccess(s -> listenLatch.countDown()));
    awaitLatch(listenLatch);
    HttpClientRequest req = client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      fail();
    });
    req.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        testComplete();
      });
    });
    req.sendHead();
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
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    });
    await();
  }

  @Test
  public void testFollowRedirectGetOn301() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 301, 200, 2, "http://localhost:8080/redirected", "http://localhost:8080/redirected");
  }

  @Test
  public void testFollowRedirectPostOn301() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.GET, 301, 301, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectPutOn301() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.GET, 301, 301, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectGetOn302() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 302, 200, 2, "http://localhost:8080/redirected", "http://localhost:8080/redirected");
  }

  @Test
  public void testFollowRedirectPostOn302() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.GET, 302, 302, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectPutOn302() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.GET, 302, 302, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectGetOn303() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 303, 200, 2, "http://localhost:8080/redirected", "http://localhost:8080/redirected");
  }

  @Test
  public void testFollowRedirectPostOn303() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.GET, 303, 200, 2, "http://localhost:8080/redirected", "http://localhost:8080/redirected");
  }

  @Test
  public void testFollowRedirectPutOn303() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.GET, 303, 200, 2, "http://localhost:8080/redirected", "http://localhost:8080/redirected");
  }

  @Test
  public void testFollowRedirectNotOn304() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 304, 304, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectGetOn307() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 307, 200, 2, "http://localhost:8080/redirected", "http://localhost:8080/redirected");
  }

  @Test
  public void testFollowRedirectPostOn307() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.POST, 307, 307, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectPutOn307() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.PUT, 307, 307, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectWithRelativeLocation() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 301, 200, 2, "/another", "http://localhost:8080/another");
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
        resp.end();
      }
    });
    startServer();
    client.request(method, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(resp.request().absoluteURI(), t);
      assertEquals(expectedRequests, numRequests.get());
      assertEquals(expectedStatus, resp.statusCode());
      testComplete();
    }).
        putHeader("foo", "foo_value").
        setFollowRedirects(true).
        end();
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
          req.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, scheme + "://localhost:8080/whatever").end();
        });
      } else {
        assertEquals(HttpMethod.GET, req.method());
        req.response().end();
      }
    });
    startServer();
    client.put(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }).setFollowRedirects(true).end(translator.apply(expected));
    await();
  }

  @Test
  public void testFollowRedirectWithChunkedBody() throws Exception {
    Buffer buff1 = Buffer.buffer(TestUtils.randomAlphaString(2048));
    Buffer buff2 = Buffer.buffer(TestUtils.randomAlphaString(2048));
    Buffer expected = Buffer.buffer().appendBuffer(buff1).appendBuffer(buff2);
    AtomicBoolean redirected = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      boolean redirect = redirected.compareAndSet(false, true);
      if (redirect) {
        latch.countDown();
      }
      if (redirect) {
        assertEquals(HttpMethod.PUT, req.method());
        req.bodyHandler(body -> {
          assertEquals(body, expected);
          String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
          req.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, scheme + "://localhost:8080/whatever").end();
        });
      } else {
        assertEquals(HttpMethod.GET, req.method());
        req.response().end();
      }
    });
    startServer();
    HttpClientRequest req = client.put(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }).setFollowRedirects(true)
      .setChunked(true)
      .write(buff1);
    awaitLatch(latch);
    req.end(buff2);
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
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      boolean redirect = redirected.compareAndSet(false, true);
      if (redirect) {
        Buffer body = Buffer.buffer();
        req.handler(buff -> {
          if (body.length() == 0) {
            HttpServerResponse resp = req.response();
            String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
            resp.setStatusCode(303).putHeader(HttpHeaders.LOCATION, scheme + "://localhost:8080/whatever");
            if (expectFail) {
              resp.setChunked(true).write("whatever");
              vertx.runOnContext(v -> {
                resp.close();
              });
            } else {
              resp.end();
            }
            latch.countDown();
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
    HttpClientRequest req = client.put(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }).setFollowRedirects(true)
        .exceptionHandler(err -> {
          if (expectFail) {
            if (called.compareAndSet(false, true)) {
              testComplete();
            }
          } else {
            fail(err);
          }
        })
        .setChunked(true)
        .write(buff1);
    awaitLatch(latch);
    // Wait so we end the request while having received the server response (but we can't be notified)
    if (!expectFail) {
      Thread.sleep(500);
      req.end(buff2);
    }
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
    HttpClientRequest req = client.put(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }).setFollowRedirects(true);
    req.putHeader("Content-Length", "" + expected.length());
    req.exceptionHandler(this::fail);
    req.sendHead(v -> {
      req.end(expected);
    });
    await();
  }

  @Test
  public void testFollowRedirectLimit() throws Exception {
    AtomicInteger redirects = new AtomicInteger();
    server.requestHandler(req -> {
      int val = redirects.incrementAndGet();
      if (val > 16) {
        fail();
      } else {
        String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
        req.response().setStatusCode(301).putHeader(HttpHeaders.LOCATION, scheme + "://localhost:8080/otherpath").end();
      }
    });
    startServer();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(16, redirects.get());
      assertEquals(301, resp.statusCode());
      assertEquals("/otherpath", resp.request().path());
      testComplete();
    }).setFollowRedirects(true).end();
    await();
  }

  @Test
  public void testFollowRedirectPropagatesTimeout() throws Exception {
    AtomicInteger redirections = new AtomicInteger();
    server.requestHandler(req -> {
      switch (redirections.getAndIncrement()) {
        case 0:
          String scheme = createBaseServerOptions().isSsl() ? "https" : "http";
          req.response().setStatusCode(307).putHeader(HttpHeaders.LOCATION, scheme + "://localhost:8080/whatever").end();
          break;
      }
    });
    startServer();
    AtomicBoolean done = new AtomicBoolean();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      fail();
    }).setFollowRedirects(true)
      .exceptionHandler(err -> {
        if (done.compareAndSet(false, true)) {
          assertEquals(2, redirections.get());
          testComplete();
        }
      })
      .setTimeout(500).end();
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
    startServer();
    HttpServer server2 = vertx.createHttpServer(options);
    server2.requestHandler(req -> {
      assertEquals(1, redirects.get());
      assertEquals(scheme + "://localhost:" + port + "/whatever", req.absoluteURI());
      req.response().end();
      complete();
    });
    startServer(server2);
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(scheme + "://localhost:" + port + "/whatever", resp.request().absoluteURI());
      complete();
    }).setFollowRedirects(true).setHost("localhost:" + options.getPort()).end();
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
    client.redirectHandler(resp -> {
      Future<HttpClientRequest> fut = Future.future();
      vertx.setTimer(25, id -> {
        HttpClientRequest req = client.getAbs(scheme + "://localhost:" + port + "/custom");
        req.putHeader("foo", "foo_another");
        req.setHost("localhost:" + port);
        fut.complete(req);
      });
      return fut;
    });
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(scheme + "://localhost:" + port + "/custom", resp.request().absoluteURI());
      complete();
    }).setFollowRedirects(true).putHeader("foo", "foo_value").setHost("localhost:" + options.getPort()).end();
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
    testFoo("http://:8080/somepath", null);
  }

  private void testFoo(String location, String expected) throws Exception {
    int status = 301;
    Map<String, String> headers = Collections.singletonMap(HttpHeaders.LOCATION.toString(), location);
    HttpMethod method = HttpMethod.GET;
    String baseURI = "https://localhost:8080";
    class MockReq implements HttpClientRequest {
      public HttpClientRequest exceptionHandler(Handler<Throwable> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest write(Buffer data) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setWriteQueueMaxSize(int maxSize) { throw new UnsupportedOperationException(); }
      public HttpClientRequest drainHandler(Handler<Void> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest handler(Handler<HttpClientResponse> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest pause() { throw new UnsupportedOperationException(); }
      public HttpClientRequest resume() { throw new UnsupportedOperationException(); }
      public HttpClientRequest fetch(long amount) { throw new UnsupportedOperationException(); }
      public HttpClientRequest endHandler(Handler<Void> endHandler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setFollowRedirects(boolean followRedirects) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setChunked(boolean chunked) { throw new UnsupportedOperationException(); }
      public boolean isChunked() { return false; }
      public HttpMethod method() { return method; }
      public String getRawMethod() { throw new UnsupportedOperationException(); }
      public HttpClientRequest setRawMethod(String method) { throw new UnsupportedOperationException(); }
      public String absoluteURI() { return baseURI; }
      public String uri() { throw new UnsupportedOperationException(); }
      public String path() { throw new UnsupportedOperationException(); }
      public String query() { throw new UnsupportedOperationException(); }
      public HttpClientRequest setHost(String host) { throw new UnsupportedOperationException(); }
      public String getHost() { throw new UnsupportedOperationException(); }
      public MultiMap headers() { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(String name, String value) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(CharSequence name, CharSequence value) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(String name, Iterable<String> values) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) { throw new UnsupportedOperationException(); }
      public HttpClientRequest write(String chunk) { throw new UnsupportedOperationException(); }
      public HttpClientRequest write(String chunk, String enc) { throw new UnsupportedOperationException(); }
      public HttpClientRequest continueHandler(@Nullable Handler<Void> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest sendHead() { throw new UnsupportedOperationException(); }
      public HttpClientRequest sendHead(Handler<HttpVersion> completionHandler) { throw new UnsupportedOperationException(); }
      public void end(String chunk) { throw new UnsupportedOperationException(); }
      public void end(String chunk, String enc) { throw new UnsupportedOperationException(); }
      public void end(Buffer chunk) { throw new UnsupportedOperationException(); }
      public void end() { throw new UnsupportedOperationException(); }
      public HttpClientRequest setTimeout(long timeoutMs) { throw new UnsupportedOperationException(); }
      public HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) { throw new UnsupportedOperationException(); }
      public boolean reset(long code) { return false; }
      public HttpConnection connection() { throw new UnsupportedOperationException(); }
      public HttpClientRequest connectionHandler(@Nullable Handler<HttpConnection> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload) { throw new UnsupportedOperationException(); }
      public boolean writeQueueFull() { throw new UnsupportedOperationException(); }
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
      public HttpClientResponse bodyHandler(Handler<Buffer> bodyHandler) { throw new UnsupportedOperationException(); }
      public HttpClientResponse customFrameHandler(Handler<HttpFrame> handler) { throw new UnsupportedOperationException(); }
      public NetSocket netSocket() { throw new UnsupportedOperationException(); }
      public HttpClientRequest request() { return req; }
    }
    MockResp resp = new MockResp();
    Function<HttpClientResponse, Future<HttpClientRequest>> handler = client.redirectHandler();
    Future<HttpClientRequest> redirection = handler.apply(resp);
    if (expected != null) {
      assertEquals(location, redirection.result().absoluteURI());
    } else {
      assertTrue(redirection == null || redirection.failed());
    }
  }

  @Test
  public void testFollowRedirectEncodedParams() throws Exception {
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
            .end();
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
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/first/call/from/client", resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }).setFollowRedirects(true).end();
    await();
  }

  @Test
  public void testServerResponseCloseHandlerNotHoldingLock() throws Exception {
    server.requestHandler(req -> {
      req.response().closeHandler(v -> {
        assertFalse(Thread.holdsLock(req.connection()));
        testComplete();
      });
      req.response().setChunked(true).write("hello");
    });
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      assertEquals(200, resp.statusCode());
      resp.request().connection().close();
    });
    await();
  }

  @Test
  public void testCloseHandlerWhenConnectionEnds() throws Exception {
    server.requestHandler(req -> {
      req.response().endHandler(v -> {
        testComplete();
      });
      req.response().end("some-data");
    });
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      resp.handler(v -> {
        resp.request().connection().close();
      });
    });
    await();
  }

  @Test
  public void testCloseHandlerWhenConnectionClose() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true).write("some-data");
      resp.closeHandler(v -> {
        checkHttpServerResponse(resp);
        testComplete();
      });
    });
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      resp.handler(v -> {
        resp.request().connection().close();
      });
    });
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
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
      resp.endHandler(v -> {
        resp.request().connection().close();
      });
    });
    await();
  }

  private TestLoggerFactory testLogging() throws Exception {
    return TestUtils.testLogging(() -> {
   	  try {
        server.requestHandler(req -> {
          req.response().end();
        });
        startServer();
        client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
          testComplete();
        });
        await();
   	  } catch (Exception e) {
   	    throw new RuntimeException(e);
   	  }
    });
  }

  @Test
  public void testClientDecompressionError() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response()
        .putHeader("Content-Encoding", "gzip")
        .end("long response with mismatched encoding causes connection leaks");
    });
    startServer();
    AtomicInteger exceptionCount = new AtomicInteger();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setTryUseCompression(true));
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      resp.exceptionHandler(err -> {
        if (exceptionCount.incrementAndGet() == 1) {
          if (err instanceof Http2Exception) {
            complete();
            // Connection is not closed for HTTP/2 only the streams so we need to force it
            resp.request().connection().close();
          } else if (err instanceof DecompressionException) {
            complete();
          }
        }
      });
    }).connectionHandler(conn -> {
      conn.closeHandler(v -> {
        complete();
      });
    }).end();

    await();

  }

  @Test
  public void testContainsValueString() {
    server.requestHandler(req -> {
      assertTrue(req.headers().contains("Foo", "foo", false));
      assertFalse(req.headers().contains("Foo", "fOo", false));
      req.response().putHeader("quux", "quux");
      req.response().end();
    });
    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertTrue(resp.headers().contains("Quux", "quux", false));
        assertFalse(resp.headers().contains("Quux", "quUx", false));
        testComplete();
      });
      req.putHeader("foo", "foo");
      req.end();
    }));
    await();
  }

  @Test
  public void testContainsValueStringIgnoreCase() {
    server.requestHandler(req -> {
      assertTrue(req.headers().contains("Foo", "foo", true));
      assertTrue(req.headers().contains("Foo", "fOo", true));
      req.response().putHeader("quux", "quux");
      req.response().end();
    });
    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertTrue(resp.headers().contains("Quux", "quux", true));
        assertTrue(resp.headers().contains("Quux", "quUx", true));
        testComplete();
      });
      req.putHeader("foo", "foo");
      req.end();
    }));
    await();
  }

  @Test
  public void testContainsValueCharSequence() {
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
    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertTrue(resp.headers().contains(Quux, quux, false));
        assertFalse(resp.headers().contains(Quux, quUx, false));
        testComplete();
      });
      req.putHeader(foo, foo);
      req.end();
    }));
    await();
  }

  @Test
  public void testContainsValueCharSequenceIgnoreCase() {
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
    server.listen(onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertTrue(resp.headers().contains(Quux, quux, true));
        assertTrue(resp.headers().contains(Quux, quUx, true));
        testComplete();
      });
      req.putHeader(foo, foo);
      req.end();
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
    startServer();
    client.post(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      resp.bodyHandler(buff -> {
        testComplete();
      });
    }).exceptionHandler(this::fail)
      .putHeader("content-length", String.valueOf(length))
      .write(expected)
      .end();
    await();
  }

  @Test
  public void testClientSynchronousConnectFailures() {
    System.setProperty("vertx.disableDnsResolver", "true");
    Vertx vertx = Vertx.vertx(new VertxOptions().setAddressResolverOptions(new AddressResolverOptions().setQueryTimeout(100)));
    try {
      int poolSize = 2;
      HttpClient client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(poolSize));
      AtomicInteger failures = new AtomicInteger();
      vertx.runOnContext(v -> {
        for (int i = 0; i < (poolSize + 1); i++) {
          HttpClientRequest clientRequest = client.getAbs("http://invalid-host-name.foo.bar", resp -> fail());
          AtomicBoolean f = new AtomicBoolean();
          clientRequest.exceptionHandler(e -> {
            if (f.compareAndSet(false, true)) {
              if (failures.incrementAndGet() == poolSize + 1) {
                testComplete();
              }
            }
          });
          clientRequest.end();
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
    client.get(-1, "localhost", "/someuri", resp -> {
      fail();
    }).exceptionHandler(err -> {
      assertEquals(err.getClass(), IllegalArgumentException.class);
      assertEquals(err.getMessage(), "port p must be in range 0 <= p <= 65535");
      testComplete();
    }).end();
    await();
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

  protected static String generateQueryString(Map<String, String> params, char delim) {
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

  protected static Map<String, String> genMap(int num) {
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

  protected static MultiMap getHeaders(int num) {
    Map<String, String> map = genMap(num);
    MultiMap headers = new HeadersAdaptor(new DefaultHttpHeaders());
    for (Map.Entry<String, String> entry : map.entrySet()) {
      headers.add(entry.getKey(), entry.getValue());
    }
    return headers;
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
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {});
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
    startServer();
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
      resp.headers().forEach(header -> {
        String name = header.getKey();
        switch (name.toLowerCase()) {
          case ":status":
          case "content-length":
            break;
          default:
            fail("Unexpected header " + name);
        }
      });
      testComplete();
    });
    await();
  }

  @Test
  public void testDisableIdleTimeoutInPool() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions()
      .setIdleTimeout(1)
      .setMaxPoolSize(1)
      .setKeepAliveTimeout(10)
    );
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
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
    });
    await();
  }

  @Test
  public void testHttpConnect() {
    Buffer buffer = TestUtils.randomBuffer(128);
    Buffer received = Buffer.buffer();
    CompletableFuture<Void> closeSocket = new CompletableFuture<>();
    vertx.createNetServer(new NetServerOptions().setPort(1235)).connectHandler(socket -> {
      socket.handler(socket::write);
      closeSocket.thenAccept(v -> {
        socket.close();
      });
    }).listen(onSuccess(netServer -> {
      server.requestHandler(req -> {
        vertx.createNetClient(new NetClientOptions()).connect(netServer.actualPort(), "localhost", onSuccess(dst -> {

          req.response().setStatusCode(200);
          req.response().setStatusMessage("Connection established");

          // Now create a NetSocket
          NetSocket src = req.netSocket();

          // Create pumps which echo stuff
          Pump.pump(src, dst).start();
          Pump.pump(dst, src).start();
          dst.closeHandler(v -> {
            src.close();
          });
        }));
      });
      server.listen(onSuccess(s -> {
        client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
          assertEquals(200, resp.statusCode());
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
        }).sendHead();
      }));
    }));

    await();
  }

  @Test
  public void testAccessNetSocketPendingResponseDataPaused() {
    testAccessNetSocketPendingResponseData(true);
  }

  @Test
  public void testAccessNetSocketPendingResponseDataNotPaused() {
    testAccessNetSocketPendingResponseData(false);
  }

  private void testAccessNetSocketPendingResponseData(boolean pause) {
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.write("hello");
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        NetSocket so = resp.netSocket();
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
      });
      req.sendHead();
    }));

    await();
  }

  @Test
  public void testHttpInvalidConnectResponseEnded() {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
      try {
        req.netSocket();
        fail();
      } catch (IllegalStateException e) {
        complete();
      }
    });
    server.listen(onSuccess(s -> {
      client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }).end();
    }));

    await();
  }

  @Test
  public void testHttpInvalidConnectResponseChunked() {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().setChunked(true).write("some-chunk");
      try {
        req.netSocket();
        fail();
      } catch (IllegalStateException e) {
        complete();
      }
    });
    server.listen(onSuccess(s -> {
      client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }).end();
    }));

    await();
  }

  /*
  @Test
  public void testReset() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        System.out.println("GOT ERR");
      });
      req.endHandler(v -> {
        System.out.println("GOT END");
        latch.countDown();
      });
    });
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {});
    req.end();
    awaitLatch(latch);
    req.reset();

    await();
  }
*/
}
