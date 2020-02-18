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

import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Exception;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.net.*;
import io.vertx.core.streams.Pump;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakestream.FakeStream;
import io.vertx.test.netty.TestLoggerFactory;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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
  private File tmp;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testDir = testFolder.newFolder();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", USE_NATIVE_TRANSPORT);
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    }
  }

  @Test
  public void testClientRequestArguments() throws Exception {
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(noOpHandler());
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
    assertIllegalArgumentException(() -> req.setTimeout(0));
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
    int len = 3;
    waitFor(len * len);
    List<SocketAddress> addresses = new ArrayList<>();
    for (int i = 0;i < len;i++) {
      File sockFile = TestUtils.tmpFile(".sock");
      SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
      HttpServer server = vertx
        .createHttpServer(createBaseServerOptions())
        .requestHandler(req -> req.response().end(sockAddress.path()));
      startServer(sockAddress, server);
      addresses.add(sockAddress);
    }
    for (int i = 0;i < len;i++) {
      SocketAddress sockAddress = addresses.get(i);
      for (int j = 0;j < len;j++) {
        client.request(sockAddress, new RequestOptions()
          .setHost(DEFAULT_HTTP_HOST)
          .setPort(DEFAULT_HTTP_PORT)
          .setURI(DEFAULT_TEST_URI))
          .setHandler(onSuccess(resp -> {
            resp.body(onSuccess(body -> {
              assertEquals(sockAddress.path(), body.toString());
              complete();
            }));
          })).end();
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

    server.listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
        assertEquals("quux", resp.headers().get("Quux"));
        assertEquals("quux", resp.headers().get("quux"));
        assertEquals("quux", resp.headers().get("qUUX"));
        assertTrue(resp.headers().contains("Quux"));
        assertTrue(resp.headers().contains("quux"));
        assertTrue(resp.headers().contains("qUUX"));
        testComplete();
      }));

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
          vertx.createHttpClient(createBaseClientOptions()).get(ar.result().actualPort(), DEFAULT_HTTP_HOST, "/", onSuccess(response -> {
            assertEquals(response.statusCode(), 200);
            response.bodyHandler(body -> {
              assertEquals(body.toString("UTF-8"), "hello");
              testComplete();
            });
          }));
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
          vertx.createHttpClient(createBaseClientOptions()).get(ar.result().actualPort(), DEFAULT_HTTP_HOST, "/", onSuccess(response -> {
            assertEquals(response.statusCode(), 200);
            response.bodyHandler(body -> {
              assertEquals(body.toString("UTF-8"), "hello");
              testComplete();
            });
          }));
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
          vertx.createHttpClient(createBaseClientOptions()).get(ar.result().actualPort(), DEFAULT_HTTP_HOST, "/", onSuccess(response -> {
            assertEquals(response.statusCode(), 200);
            response.bodyHandler(body -> {
              assertEquals(body.toString("UTF-8"), "hello");
              testComplete();
            });
          }));
        });
    await();
  }

  @Test
  public void testRequestNPE() {
    String uri = "/some-uri?foo=bar";
    TestUtils.assertNullPointerException(() -> client.request(null, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri));
    TestUtils.assertNullPointerException(() -> client.request( null, 8080, "localhost", "/somepath"));
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, 8080, null, "/somepath"));
    TestUtils.assertNullPointerException(() -> client.request(HttpMethod.GET, 8080, "localhost", null));
  }

  @Test
  public void testInvalidAbsoluteURI() {
    try {
      client.request(new RequestOptions().setAbsoluteURI("ijdijwidjqwoijd192d192192ej12d")).setHandler(noOpHandler()).end();
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
    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }))
        .putHeader("foo", "bar")
        .end();
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
    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(singletonList("http://example2.org"), resp.headers().getAll("LocatioN"));
          testComplete();
        }))
        .end();
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
      req = client.request(testAddress, new RequestOptions().setMethod(method).setAbsoluteURI((ssl ? "https://" : "http://") + DEFAULT_HTTP_HOST + ":" + DEFAULT_HTTP_PORT + uri));
    } else {
      req = client.request(method, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri);
    }
    req.setHandler(onSuccess(handler::handle));
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

    server.listen(testAddress, onSuccess(server -> request.end()));

    await();
  }

  @Test
  public void testServerChaining() {
    server.requestHandler(req -> {
      assertTrue(req.response().setChunked(true) == req.response());
      testComplete();
    });

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(noOpHandler()).end();
    }));

    await();
  }

  @Test
  public void testServerChainingSendFile() throws Exception {
    File file = setupFile("test-server-chaining.dat", "blah");
    server.requestHandler(req -> {
      assertTrue(req.response().sendFile(file.getAbsolutePath(), null) == req.response());
//      assertTrue(req.response().ended());
      file.delete();
      testComplete();
    });

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(noOpHandler()).end();
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
    }).listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          assertEquals("wibble", res.headers().get("extraheader"));
          complete();
        })).end();
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
    }).listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          assertEquals("wibble", res.headers().get("extraheader"));
          res.bodyHandler(buff -> {
            assertEquals(Buffer.buffer(content), buff);
            complete();
          });
        })).end();
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
    }).listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          assertEquals("wibble", res.headers().get("extraheader"));
          res.bodyHandler(buff -> {
            assertEquals(Buffer.buffer(content.toString()), buff);
            complete();
          });
        })).end();
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
    }).listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          assertEquals("wibble", res.headers().get("extraheader"));
          res.bodyHandler(buff -> {
            assertEquals(Buffer.buffer(content), buff);
            complete();
          });
        })).end();
    }));
    await();
  }

  @Test
  public void testResponseEndHandlersConnectionClose() {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().endHandler(v -> complete());
      req.response().end();
    }).listen(onSuccess(server ->
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(res -> {
          assertEquals(200, res.statusCode());
          complete();
        }))
        .putHeader(HttpHeaders.CONNECTION, "close")
        .end()));
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

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri)
        .setHandler(resp -> testComplete())
        .end();
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
    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
        .setHandler(onSuccess(resp -> testComplete()))
        .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED)
        .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(postData.length()))
        .end(postData);
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

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "some-uri/?" + query)
        .setHandler(resp -> testComplete())
        .end();
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
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(resp -> testComplete())
        .end();
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
        assertEquals(0, req.headers().size());
      }
      req.response().end();
    });

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(resp -> testComplete())
        .end();
    }));

    await();
  }

  @Test
  public void testRequestHeadersWithCharSequence() {
    HashMap<CharSequence, String> expectedHeaders = new HashMap<>();
    expectedHeaders.put(HttpHeaders.TEXT_HTML, "text/html");
    expectedHeaders.put(HttpHeaders.USER_AGENT, "User-Agent");
    expectedHeaders.put(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED, "application/x-www-form-urlencoded");

    server.requestHandler(req -> {

      MultiMap headers = req.headers();
      headers.remove("host");

      assertEquals(expectedHeaders.size(), headers.size());

      expectedHeaders.forEach((k, v) -> assertEquals(v, headers.get(k)));
      expectedHeaders.forEach((k, v) -> assertEquals(v, req.getHeader(k)));

      req.response().end();
    });

    server.listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(resp -> testComplete());

      expectedHeaders.forEach((k, v) -> req.headers().add(k, v));

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

    server.listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(resp -> testComplete());
      if (individually) {
        for (Map.Entry<String, String> header : expectedHeaders) {
          req.headers().add(header.getKey(), header.getValue());
        }
      } else {
        req.headers().setAll(expectedHeaders);
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

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertTrue(headers.size() < resp.headers().size());
          for (Map.Entry<String, String> entry : headers) {
            assertEquals(entry.getValue(), resp.headers().get(entry.getKey()));
            assertEquals(entry.getValue(), resp.getHeader(entry.getKey()));
          }
          testComplete();
        })).end();
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

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(onSuccess(resp -> {
        assertTrue(headers.size() < resp.headers().size());

        headers.forEach((k, v) -> assertEquals(v, resp.headers().get(k)));
        headers.forEach((k, v) -> assertEquals(v, resp.getHeader(k)));

        testComplete();
      })).end();
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

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertEquals(cookies.size(), resp.cookies().size());
            for (int i = 0; i < cookies.size(); ++i) {
              assertEquals(cookies.get(i), resp.cookies().get(i));
            }
            testComplete();
          });
        })).end();
    }));

    await();
  }

  @Test
  public void testUseRequestAfterComplete() {
    server.requestHandler(noOpHandler());

    server.listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(noOpHandler());
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
  public void testRequestBodyBufferAtEnd() {
    Buffer body = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> req.bodyHandler(buffer -> {
      assertEquals(body, buffer);
      req.response().end();
    }));

    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(resp -> testComplete())
        .end(body);
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

    server.listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(noOpHandler());
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

    server.listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(resp -> testComplete());
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

    server.listen(testAddress, onSuccess(server -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(noOpHandler());

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
    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(noOpHandler());
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

  @Ignore
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
  public void testClientExceptionHandlerCalledWhenFailingToConnect() {
    waitFor(2);
    client.request(HttpMethod.GET, testAddress, 9998, "255.255.255.255", DEFAULT_TEST_URI)
      .setHandler(onFailure(err ->
        complete()))
      .exceptionHandler(error -> {
        complete();
      })
      .end();
    await();
  }

  @Repeat(times = 10)
  @Test
  public void testClientExceptionHandlerCalledWhenServerTerminatesConnection() throws Exception {
    int numReqs = 10;
    waitFor(numReqs);
    server.requestHandler(request -> {
      request.response().close();
    }).listen(testAddress, onSuccess(s -> {
      // Exception handler should be called for any requests in the pipeline if connection is closed
      for (int i = 0; i < numReqs; i++) {
        client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(onFailure(err -> {
            complete();
          })).end();
      }
    }));
    await();
  }

  @Test
  public void testClientExceptionHandlerCalledWhenServerTerminatesConnectionAfterPartialResponse() throws Exception {
    server.requestHandler(request -> {
      //Write partial response then close connection before completing it
      HttpServerResponse resp = request.response().setChunked(true);
      resp.write("foo");
      resp.close();
    }).listen(testAddress, onSuccess(s -> {
      // Exception handler should be called for any requests in the pipeline if connection is closed
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp ->
          resp.exceptionHandler(t -> testComplete())))
        .exceptionHandler(error -> fail())
        .end();
    }));
    await();
  }

  @Test
  public void testContextExceptionHandlerCalledWhenExceptionOnDataHandler() throws Exception {
    client.close();
    server.requestHandler(request -> {
      request.response().end("foo");
    }).listen(testAddress, onSuccess(s -> {
      // Exception handler should be called for any exceptions in the data handler
      Context ctx = vertx.getOrCreateContext();
      RuntimeException cause = new RuntimeException("should be caught");
      ctx.exceptionHandler(err -> {
        if (err == cause) {
          testComplete();
        }
      });
      client = vertx.createHttpClient(createBaseClientOptions());
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.handler(data -> {
            throw cause;
          });
        })).end();
    }));
    await();
  }

  @Test
  public void testClientExceptionHandlerCalledWhenExceptionOnBodyHandler() {
    client.close();
    server.requestHandler(request -> {
      request.response().end("foo");
    }).listen(testAddress, onSuccess(s -> {
      client = vertx.createHttpClient(createBaseClientOptions());
      // Exception handler should be called for any exceptions in the data handler
      Context ctx = vertx.getOrCreateContext();
      RuntimeException cause = new RuntimeException("should be caught");
      ctx.exceptionHandler(err -> {
        if (err == cause) {
          testComplete();
        }
      });
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(data -> {
            throw cause;
          });
        })).end();
    }));
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
    }).listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            vertx.setTimer(100, tid -> testComplete());
          });
          resp.exceptionHandler(t -> {
            fail("Should not be called");
          });
        })).exceptionHandler(t -> {
        fail("Should not be called");
      }).end();
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
    }).listen(testAddress, ar -> {
      HttpClient client = vertx.createHttpClient();
      HttpClientRequest req = client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somerui")
        .setHandler(handler -> {

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
    startServer(testAddress);
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onFailure(err -> {}))
      .setChunked(true)
      .exceptionHandler(err -> {
      testComplete();
    }).write("chunk");
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
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        resp.handler(buff -> {
          conn.get().close();
        });
        resp.exceptionHandler(err -> {
          testComplete();
        });
      })).end();
    await();
  }

  @Test
  public void testClientRequestExceptionHandlerCalledWhenRequestEnded() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer(testAddress);
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onFailure(err -> {
        complete();
      }));
    req.exceptionHandler(this::fail);
    req.end();
    try {
      req.exceptionHandler(err -> fail());
      fail();
    } catch (Exception e) {
      complete();
    }
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
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
        })).end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertEquals(trailers.size(), resp.trailers().size());
            for (Map.Entry<String, String> entry : trailers) {
              assertEquals(entry.getValue(), resp.trailers().get(entry.getKey()));
              assertEquals(entry.getValue(), resp.getTrailer(entry.getKey()));
            }
            testComplete();
          });
        })).end();
    }));

    await();
  }

  @Test
  public void testResponseNoTrailers() {
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.response().end();
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertTrue(resp.trailers().isEmpty());
            testComplete();
          });
        })).end();
    }));

    await();
  }

  @Test
  public void testUseAfterServerResponseEnd() throws Exception {
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
      assertIllegalStateException(() -> resp.sendFile("webroot/somefile.html", ar -> {}));
      assertIllegalStateException(() -> resp.end(ar -> {}));
      assertIllegalStateException(() -> resp.end("foo", ar -> {}));
      assertIllegalStateException(() -> resp.end(buff, ar -> {}));
      assertIllegalStateException(() -> resp.end("foo", "UTF-8", ar -> {}));
      assertIllegalStateException(() -> resp.write(buff, ar -> {}));
      assertIllegalStateException(() -> resp.write("foo", ar -> {}));
      assertIllegalStateException(() -> resp.write("foo", "UTF-8", ar -> {}));
      assertIllegalStateException(() -> resp.write(buff, ar -> {}));
      testComplete();
    });
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(noOpHandler())
      .end();
    await();
  }

  @Test
  public void testResponseBodyBufferAtEnd() {
    Buffer body = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.response().end(body);
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals(body, buff);
            testComplete();
          });
        }))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals(body, buff);
            testComplete();
          });
        }))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals(bodyBuff, buff);
            testComplete();
          });
        }))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals(body, buff);
            testComplete();
          });
        }))
        .end();
    }));

    await();
  }

  @Test
  public void testSendFile() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, false,
      () -> client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI));
  }

  @Test
  public void testSendFileWithHandler() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, true,
      () -> client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI));
  }

  @Test
  public void testSendFileWithConnectionCloseHeader() throws Exception {
    String content = TestUtils.randomUnicodeString(1024 * 1024 * 2);
    sendFile("test-send-file.html", content, false,
      () -> client
        .request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .putHeader(HttpHeaders.CONNECTION, "close"));
  }

  private void sendFile(String fileName, String contentExpected, boolean useHandler, Supplier<HttpClientRequest> requestFact) throws Exception {
    waitFor(2);
    File fileToSend = setupFile(fileName, contentExpected);
    server.requestHandler(req -> {
      if (useHandler) {
        Handler<AsyncResult<Void>> completionHandler = onSuccess(v -> complete());
        req.response().sendFile(fileToSend.getAbsolutePath(), completionHandler);
      } else {
        req.response().sendFile(fileToSend.getAbsolutePath());
        complete();
      }
    });
    startServer(testAddress);
    requestFact.get().setHandler(onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      assertEquals("text/html", resp.headers().get("Content-Type"));
      resp.exceptionHandler(this::fail);
      resp.bodyHandler(buff -> {
        assertEquals(contentExpected, buff.toString());
        assertEquals(fileToSend.length(), Long.parseLong(resp.headers().get("content-length")));
        complete();
      });
    })).end();
    await();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals("failed", buff.toString());
            testComplete();
          });
        }))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(file.length(), Long.parseLong(resp.headers().get("content-length")));
          assertEquals("wibble", resp.headers().get("content-type"));
          resp.bodyHandler(buff -> {
            assertEquals(content, buff.toString());
            file.delete();
            testComplete();
          });
        })).end();
    }));

    await();
  }

  @Test
  public void testSendFileNotFound() throws Exception {

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile("nosuchfile.html");
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onFailure(err -> {}))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onFailure(resp -> {}))
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onFailure(err -> {}))
        .end();
    }));

    await();
  }

  @Test
  public void testSendOpenRangeFileFromClasspath() {
    server.requestHandler(res -> {
      res.response().sendFile("webroot/somefile.html", 6);
    }).listen(testAddress, onSuccess(res -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertTrue(buff.toString().startsWith("<body>blah</body></html>"));
            testComplete();
          });
        })).end();
    }));
    await();
  }

  @Test
  public void testSendRangeFileFromClasspath() {
    server.requestHandler(res -> {
      res.response().sendFile("webroot/somefile.html", 6, 6);
    }).listen(testAddress, onSuccess(res -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals("<body>", buff.toString());
            testComplete();
          });
        })).end();
    }));
    await();
  }

  @Test
  public void test100ContinueHandledAutomatically() {
    Buffer toSend = TestUtils.randomBuffer(1000);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setHandle100ContinueAutomatically(true));

    server.requestHandler(req -> {
      req.bodyHandler(data -> {
        assertEquals(toSend, data);
        req.response().end();
      });
    });

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
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
  public void test100ContinueHandledManually() {

    Buffer toSend = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> {
      assertEquals("100-continue", req.getHeader("expect"));
      req.response().writeContinue();
      req.bodyHandler(data -> {
        assertEquals(toSend, data);
        req.response().end();
      });
    });

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
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
  public void test100ContinueRejectedManually() {

    server.requestHandler(req -> {
      req.response().setStatusCode(405).end();
      req.bodyHandler(data -> {
        fail("body should not be received");
      });
    });

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
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

    client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onFailure(err -> {
      complete();
    }))
      .exceptionHandler(err -> fail())
      .putHeader("Expect", "100-continue")
      .continueHandler(v -> complete())
      .end();

    await();
  }

  @Test
  public void testClientDrainHandler() {
    pausingServer(resumeFuture -> {
      HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI);
      req.setHandler(noOpHandler());
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

  private void pausingServer(Consumer<Promise<Void>> consumer) {
    Promise<Void> resumeFuture = Promise.promise();
    server.requestHandler(req -> {
      req.response().setChunked(true);
      req.pause();
      Context ctx = vertx.getOrCreateContext();
      resumeFuture.future().setHandler(v1 -> {
        ctx.runOnContext(v2 -> {
          req.resume();
        });
      });
      req.handler(buff -> {
        req.response().write(buff);
      });
    });

    server.listen(testAddress, onSuccess(s -> consumer.accept(resumeFuture)));
  }

  @Test
  public void testServerDrainHandler() {
    drainingServer(resumeFuture -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.pause();
          resumeFuture.setHandler(ar -> resp.resume());
        })).end();
    });

    await();
  }

  private void drainingServer(Consumer<Future<Void>> consumer) {

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

    server.listen(testAddress, onSuccess(s -> consumer.accept(resumeFuture.future())));
  }

  @Test
  public void testConnectionErrorsGetReportedToHandlers() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(4);

    // This one should cause an error in the Client Exception handler, because it has no exception handler set specifically.
    HttpClientRequest req1 = client.request(HttpMethod.GET, 9998, DEFAULT_HTTP_HOST, "someurl1")
      .setHandler(onFailure(resp -> {
      latch.countDown();
    }));

    req1.exceptionHandler(t -> {
      latch.countDown();
    });

    HttpClientRequest req2 = client.request(HttpMethod.GET, 9997, DEFAULT_HTTP_HOST, "someurl2")
      .setHandler(onFailure(resp -> {
      latch.countDown();
    }));

    AtomicInteger req2Exceptions = new AtomicInteger();
    req2.exceptionHandler(t -> {
      assertEquals("More than one call to req2 exception handler was not expected", 1, req2Exceptions.incrementAndGet());
      latch.countDown();
    });

    req1.end();
    req2.sendHead();

    awaitLatch(latch);
    testComplete();
  }

  @Test
  public void testRequestTimesoutWhenIndicatedPeriodExpiresWithoutAResponseFromRemoteServer() {
    server.requestHandler(noOpHandler()); // No response handler so timeout triggers
    AtomicBoolean failed = new AtomicBoolean();
    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onFailure(t -> {
          // Catch the first, the second is going to be a connection closed exception when the
          // server is shutdown on testComplete
          if (failed.compareAndSet(false, true)) {
            testComplete();
          }
        }))
        .setTimeout(1000)
        .end();
    }));

    await();
  }

  @Test
  public void testRequestTimeoutCanceledWhenRequestHasAnOtherError() {
    AtomicReference<Throwable> exception = new AtomicReference<>();
    // There is no server running, should fail to connect
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onFailure(exception::set))
      .setTimeout(800)
      .end();

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

    server.listen(testAddress, onSuccess(s -> {
      AtomicReference<Throwable> exception = new AtomicReference<>();

      // There is no server running, should fail to connect
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(noOpHandler())
        .exceptionHandler(exception::set)
        .setTimeout(500)
        .end();

      vertx.setTimer(1000, id -> {
        assertNull("Did not expect any exception", exception.get());
        testComplete();
      });
    }));

    await();
  }

  @Test
  public void testHttpClientRequestTimeoutResetsTheConnection() throws Exception {
    waitFor(3);
    server.requestHandler(req -> {
      AtomicBoolean errored = new AtomicBoolean();
      req.exceptionHandler(err -> {
        if (errored.compareAndSet(false, true)) {
          complete();
        }
      });
    });
    startServer(testAddress);
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onFailure(err -> {
        complete();
      }));
    AtomicBoolean errored = new AtomicBoolean();
    req.exceptionHandler(err -> {
      if (errored.compareAndSet(false, true)) {
        complete();
      }
    });
    CountDownLatch latch = new CountDownLatch(1);
    req.setChunked(true).sendHead(version -> latch.countDown());
    awaitLatch(latch);
    req.setTimeout(100);
    await();
  }

  @Test
  public void testConnectInvalidPort() {
    waitFor(2);
    client.request(HttpMethod.GET, 9998, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(onFailure(err -> complete()))
      .exceptionHandler(t -> complete())
      .end();
    await();
  }

  @Test
  public void testConnectInvalidHost() {
    waitFor(2);
    client.request(HttpMethod.GET, 9998, "255.255.255.255", DEFAULT_TEST_URI).setHandler(onFailure(resp -> complete()))
      .exceptionHandler(t -> complete())
      .end();
    await();
  }

  @Test
  public void testSetHandlersAfterListening() {
    server.requestHandler(noOpHandler());

    server.listen(testAddress, onSuccess(s -> {
      assertIllegalStateException(() -> server.requestHandler(noOpHandler()));
      assertIllegalStateException(() -> server.webSocketHandler(noOpHandler()));
      testComplete();
    }));

    await();
  }

  @Test
  public void testSetHandlersAfterListening2() {
    server.requestHandler(noOpHandler());

    server.listen(testAddress, onSuccess(v -> testComplete()));
    assertIllegalStateException(() -> server.requestHandler(noOpHandler()));
    assertIllegalStateException(() -> server.webSocketHandler(noOpHandler()));
    await();
  }

  @Test
  public void testListenNoHandlers() {
    assertIllegalStateException(() -> server.listen(ar -> {
    }));
  }

  @Test
  public void testListenNoHandlers2() {
    assertIllegalStateException(() -> server.listen());
  }

  @Test
  public void testListenTwice() {
    server.requestHandler(noOpHandler());
    server.listen(testAddress, onSuccess(v -> testComplete()));
    assertIllegalStateException(() -> server.listen());
    await();
  }

  @Test
  public void testListenTwice2() {
    server.requestHandler(noOpHandler());
    server.listen(testAddress, onSuccess(s -> {
      assertIllegalStateException(() -> server.listen());
      testComplete();
    }));
    await();
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
    client.request(testAddress, new RequestOptions()
      .setMethod(HttpMethod.HEAD)
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setURI(DEFAULT_TEST_URI))
      .setHandler(onSuccess(resp -> {
        assertEquals("41", resp.headers().get("Content-Length"));
        resp.endHandler(v -> testComplete());
        assertEquals("41", resp.headers().get("Content-Length"));
        resp.endHandler(v -> testComplete());
      }))
      .end();
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
      resp.headers().addAll(reqHeaders);
      resp.end();
    });
    startServer(testAddress);
    try {
      CompletableFuture<MultiMap> result = new CompletableFuture<>();
      client.request(method, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_HOST, "/")
        .setHandler(onSuccess(resp -> {
          Buffer body = Buffer.buffer();
          resp.exceptionHandler(result::completeExceptionally);
          resp.handler(body::appendBuffer);
          resp.endHandler(v -> {
            if (body.length() > 0) {
              result.completeExceptionally(new Exception());
            } else {
              result.complete(resp.headers());
            }
          });
        })).setFollowRedirects(false)
        .exceptionHandler(result::completeExceptionally)
        .end();
      return result.get(20, TimeUnit.SECONDS);
    } finally {
      client.close();
    }
  }

  @Test
  public void testHeadHasNoContentLengthByDefault() {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.HEAD, req.method());
      // By default HEAD does not have a content-length header
      req.response().end();
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.HEAD, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertNull(resp.headers().get(HttpHeaders.CONTENT_LENGTH));
          resp.endHandler(v -> testComplete());
        })).end();
    }));

    await();
  }

  @Test
  public void testHeadButCanSetContentLength() {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.HEAD, req.method());
      // By default HEAD does not have a content-length header but it can contain a content-length header
      // if explicitly set
      req.response().putHeader(HttpHeaders.CONTENT_LENGTH, "41").end();
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.HEAD, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals("41", resp.headers().get(HttpHeaders.CONTENT_LENGTH));
          resp.endHandler(v -> testComplete());
        })).end();
    }));

    await();
  }

  @Test
  public void testRemoteAddress() {
    server.requestHandler(req -> {
      if (testAddress.isInetSocket()) {
        assertEquals("127.0.0.1", req.remoteAddress().host());
      } else {
        // Returns null for domain sockets
      }
      req.response().end();
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> resp.endHandler(v -> testComplete())))
        .end();
    }));

    await();
  }

  @Test
  public void testGetAbsoluteURI() {
    server.requestHandler(req -> {
      assertEquals(req.scheme() + "://localhost:" + DEFAULT_HTTP_PORT + "/foo/bar", req.absoluteURI());
      req.response().end();
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/foo/bar")
        .setHandler(onSuccess(resp -> resp.endHandler(v -> testComplete())))
        .end();
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
  public void testPauseResumeClientResponseWontCallEndHandlePrematurely() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(8192));
    server.requestHandler(req -> {
      req.response().end(expected);
    });
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        resp.bodyHandler(body -> {
          assertEquals(expected, body);
          testComplete();
        });
        // Check that pause resume won't call the end handler prematurely
        resp.pause();
        resp.resume();
      }))
      .end();
    await();
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
    HttpClientRequest clientRequest = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
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

    server.listen(testAddress, onSuccess(s -> clientRequest.end()));

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
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setKeepAlive(true));
    for (int i = 0;i < num;i++) {
      int idx = i;
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/" + i)
        .setHandler(onSuccess(resp -> {
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
        }))
        .end();
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
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1).setKeepAlive(true));
    for (int i = 0;i < num;i++) {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals(data, buff);
            complete();
          });
          resp.pause();
          vertx.setTimer(10, id -> {
            resp.resume();
          });
        }))
        .end();
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
    HttpClientRequest req = client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        resp.endHandler(v -> {
          testComplete();
        });
      }))
      .exceptionHandler(this::fail)
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
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1));
    client.request(HttpMethod.PUT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri")
      .setHandler(resp -> {
        complete();
      })
      .end("small");
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
    client = vertx.createHttpClient(createBaseClientOptions().setMaxPoolSize(1));
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri")
      .setHandler(onSuccess(resp -> {
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
      }))
      .end();
    await();
  }

  @Test
  public void testFormUploadEmptyFile() {
    testFormUploadFile("", false, false);
  }

  @Test
  public void testFormUploadSmallFile() {
    testFormUploadFile(TestUtils.randomAlphaString(100), false, false);
  }

  @Test
  public void testFormUploadMediumFile() {
    testFormUploadFile(TestUtils.randomAlphaString(20000), false, false);
  }

  @Test
  public void testFormUploadLargeFile() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), false, false);
  }

  @Test
  public void testFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", true, false);
  }

  @Test
  public void testFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, false);
  }

  @Test
  public void testFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), true, false);
  }

  @Test
  public void testFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, false);
  }

  @Test
  public void testBrokenFormUploadEmptyFile() {
    testFormUploadFile("", true, true);
  }

  @Test
  public void testBrokenFormUploadSmallFile() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, true);
  }

  @Test
  public void testBrokenFormUploadMediumFile() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), true, true);
  }

  @Test
  public void testBrokenFormUploadLargeFile() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, true);
  }

  @Test
  public void testBrokenFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", true, true);
  }

  @Test
  public void testBrokenFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, true);
  }

  @Test
  public void testBrokenFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), true, true);
  }

  @Test
  public void testBrokenFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, true);
  }

  private void testFormUploadFile(String contentStr, boolean streamToDisk, boolean abortClient) {

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
          AtomicInteger failures = new AtomicInteger();
          upload.exceptionHandler(err -> failures.incrementAndGet());
          upload.endHandler(v -> {
            if (abortClient) {
              assertEquals(1, failures.get());
            } else {
              assertEquals(0, failures.get());
              if (streamToDisk) {
                Buffer uploaded = vertx.fileSystem().readFileBlocking(uploadedFileName);
                assertEquals(content.length(), uploaded.length());
                assertEquals(content, uploaded);
              } else {
                assertEquals(content, tot);
              }
              assertTrue(upload.isSizeAvailable());
              assertEquals(content.length(), upload.size());
            }
            AsyncFile file = upload.file();
            if (streamToDisk) {
              assertNotNull(file);
              try {
                file.flush();
                fail("Was expecting uploaded file to be closed");
              } catch (IllegalStateException ignore) {
                // File has been closed
              }
            } else {
              assertNull(file);
            }
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

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/form")
        .setHandler(ar -> {
          assertEquals(ar.failed(), abortClient);
          if (ar.succeeded()) {
            HttpClientResponse resp = ar.result();
            // assert the response
            assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              assertEquals(0, body.length());
            });
            assertEquals(0, attributeCount.get());
          }
          complete();
        });

      String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
      String epi = "\r\n" +
        "--" + boundary + "--\r\n";
      String pro = "--" + boundary + "\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
        "Content-Type: image/gif\r\n" +
        "\r\n";
      req.headers().set("content-length", "" + (pro + contentStr + epi).length());
      req.headers().set("content-type", "multipart/form-data; boundary=" + boundary);
      if (abortClient) {
        client.connectionHandler(conn -> {
          vertx.setTimer(100, id -> {
            conn.close();
          });
        });
        req.write(pro + contentStr.substring(0, contentStr.length() / 2));
      } else {
        req.end(pro + contentStr + epi);
      }
    }));

    await();
  }

  @Test
  public void testFormUploadAttributes() {
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

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/form")
        .setHandler(onSuccess(resp -> {
          // assert the response
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals(0, body.length());
          });
          assertEquals(2, attributeCount.get());
          testComplete();
        }));
      try {
        Buffer buffer = Buffer.buffer();
        // Make sure we have one param that needs url encoding
        buffer.appendString("framework=" + URLEncoder.encode("vert x", "UTF-8") + "&runson=jvm", "UTF-8");
        req.headers().set("content-length", String.valueOf(buffer.length()));
        req.headers().set("content-type", "application/x-www-form-urlencoded");
        req.end(buffer);
      } catch (UnsupportedEncodingException e) {
        fail(e.getMessage());
      }
    }));

    await();
  }

  @Test
  public void testFormUploadAttributes2() {
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

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/form")
        .setHandler(onSuccess(resp -> {
          // assert the response
          assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            assertEquals(0, body.length());
          });
          assertEquals(3, attributeCount.get());
          testComplete();
        }));
      Buffer buffer = Buffer.buffer();
      buffer.appendString("origin=junit-testUserAlias&login=admin%40foo.bar&pass+word=admin");
      req.headers().set("content-length", String.valueOf(buffer.length()));
      req.headers().set("content-type", "application/x-www-form-urlencoded");
      req.end(buffer);
    }));

    await();
  }

  @Test
  public void testHostHeaderOverridePossible() {
    server.requestHandler(req -> {
      assertEquals("localhost:4444", req.host());
      req.response().end();
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(resp -> testComplete())
        .setAuthority("localhost:4444")
        .end();
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

    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertEquals(bodyBuff, buff);
            testComplete();
          });
        }))
        .end();
    }));

    await();
  }

  @Test
  public void testResponseDataTimeout() {
    waitFor(2);
    Buffer expected = TestUtils.randomBuffer(1000);
    server.requestHandler(req -> {
      req.response().setChunked(true).write(expected);
    });
    server.listen(testAddress, onSuccess(s -> {
      Buffer received = Buffer.buffer();
      HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          AtomicInteger count = new AtomicInteger();
          resp.exceptionHandler(t -> {
            if (count.getAndIncrement() == 0) {
              assertTrue(
                t instanceof TimeoutException || /* HTTP/1 */
                  t instanceof VertxException /* HTTP/2: connection closed */);
              assertEquals(expected, received);
              complete();
            }
          });
          resp.request().setTimeout(500);
          resp.handler(received::appendBuffer);
        }));
      AtomicInteger count = new AtomicInteger();
      req.exceptionHandler(t -> {
        if (count.getAndIncrement() == 0) {
          assertTrue(
            t instanceof TimeoutException || /* HTTP/1 */
            t instanceof VertxException /* HTTP/2: connection closed */);
          assertEquals(expected, received);
          complete();
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
    }).listen(testAddress, onSuccess(s -> {
      for (int i = 0; i < numThreads; i++) {
        int index = i;
        threads[i] = new Thread() {
          public void run() {
            client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
              .setHandler(onSuccess(res -> {
                assertEquals(200, res.statusCode());
                assertEquals(String.valueOf(index), res.headers().get("count"));
                latch.countDown();
              }))
              .putHeader("count", String.valueOf(index))
              .end();
          }
        };
        threads[i].start();
      }
    }));
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
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
        });
        server.listen(testAddress, onSuccess(s -> {
          assertSame(ctx, Vertx.currentContext());
          if (!worker) {
            assertSame(thr, Thread.currentThread());
          }
          client = vertx.createHttpClient(new HttpClientOptions());
          client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
            .setHandler(onSuccess(res -> {
              assertSameEventLoop(ctx, Vertx.currentContext());
              if (!worker) {
                assertSame(thr, Thread.currentThread());
              }
              assertEquals(200, res.statusCode());
              testComplete();
            }))
            .end();
        }));
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(worker));
    await();
  }

  @Test
  public void testWorkerServer() throws Exception {
    int numReq = 5; // 5 == the HTTP/1 pool max size
    waitFor(numReq);
    CyclicBarrier barrier = new CyclicBarrier(numReq);
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
              barrier.await(20, TimeUnit.SECONDS);
            } catch (Exception e) {
              fail(e);
            }
            req.response().end("pong");
          }).connectionHandler(conn -> {
          Context current = Vertx.currentContext();
          assertTrue(Context.isOnEventLoopThread());
          assertTrue(current.isWorkerContext());
          assertSame(context, current);
          connCount.incrementAndGet(); // No complete here as we may have 1 or 5 connections depending on the protocol
        }).listen(testAddress)
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
    }, new DeploymentOptions().setWorker(true), onSuccess(id -> latch.countDown()));
    awaitLatch(latch);
    for (int i = 0;i < numReq;i++) {
      client.request(
        testAddress,
        new RequestOptions()
          .setPort(DEFAULT_HTTP_PORT)
          .setHost(DEFAULT_HTTP_HOST)
          .setURI(DEFAULT_TEST_URI))
        .setHandler(
          onSuccess(resp -> {
            complete();
          }))
        .end();
    }
    await();
    assertTrue(connCount.get() > 0);
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
    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/blah")
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          testComplete();
        }))
        .end();
    }));
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
    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.GET, testAddress, HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/blah")
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
        }))
        .end();
    }));
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
    server.listen(testAddress, onSuccess(s -> {
      String host = "localhost";
      String path = "/path";
      int port = 8080;
      client.request(HttpMethod.GET, testAddress, port, host, path).setHandler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }))
        .end();
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
    vertx.createHttpServer(createBaseServerOptions()).requestHandler(r-> {
      r.response().end();
      if (receivedRequests.incrementAndGet() == sendRequests) {
        testComplete();
      }
    }).listen(testAddress, onSuccess(s -> {
      IntStream.range(0, sendRequests)
        .forEach(x -> client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(noOpHandler())
          .end()
        );
    }));
    await();
  }

  @Test
  public void testOtherMethodRequest() {
    server.requestHandler(r -> {
      assertEquals("COPY", r.method().name());
      r.response().end();
    }).listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.valueOf("COPY"), testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
        .setHandler(onSuccess(resp -> {
          testComplete();
        }))
        .end();
    }));
    await();
  }

  @Test
  public void testClientGlobalConnectionHandler() throws Exception {
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer(testAddress);
    AtomicInteger status = new AtomicInteger();
    Handler<HttpConnection> handler = conn -> status.getAndIncrement();
    client.connectionHandler(handler);
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(resp -> {
        assertEquals(1, status.getAndIncrement());
        testComplete();
      }).end();
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
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(resp -> {
        testComplete();
      })
      .end();
    await();
  }

  @Test
  public void testServerConnectionHandlerClose() throws Exception {
    waitFor(2);
    Context serverCtx = vertx.getOrCreateContext();
    server.connectionHandler(conn -> {
      conn.close();
      conn.closeHandler(v -> {
        complete();
      });
    });
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress, serverCtx, server);
    client.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        complete();
      });
    });
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(noOpHandler())
      .end();
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
    startServer(testAddress);
    HttpClientRequest req = client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onFailure(err -> {}))
      .setChunked(true);
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
    startServer(testAddress);
    client.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        testComplete();
      });
    });
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onFailure(err -> {}))
      .sendHead();
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
      .setHandler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }))
      .end();
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

  @Test
  public void testFollowRedirectGetOn308() throws Exception {
    testFollowRedirect(HttpMethod.GET, HttpMethod.GET, 308, 200, 2, "http://localhost:8080/redirected", "http://localhost:8080/redirected");
  }

  @Test
  public void testFollowRedirectPostOn308() throws Exception {
    testFollowRedirect(HttpMethod.POST, HttpMethod.POST, 308, 308, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
  }

  @Test
  public void testFollowRedirectPutOn308() throws Exception {
    testFollowRedirect(HttpMethod.PUT, HttpMethod.PUT, 308, 308, 1, "http://localhost:8080/redirected", "http://localhost:8080/somepath");
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
    client.request(method, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        assertEquals(resp.request().absoluteURI(), t);
        assertEquals(expectedRequests, numRequests.get());
        assertEquals(expectedStatus, resp.statusCode());
        testComplete();
      }))
      .putHeader("foo", "foo_value")
      .setFollowRedirects(true)
      .end();
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
    client.put(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/somepath")
      .setFollowRedirects(true), translator.apply(expected), onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    }));
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
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
    })).setFollowRedirects(true)
      .setChunked(true);
    req.write(buff1);
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
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(ar -> {
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
      })
      .setFollowRedirects(true)
      .setChunked(true);
    req.write(buff1);
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
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        testComplete();
      }))
      .setFollowRedirects(true)
      .putHeader("Content-Length", "" + expected.length())
      .exceptionHandler(this::fail);
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
    client.get(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/somepath")
      .setFollowRedirects(true), onSuccess(resp -> {
      assertEquals(16, redirects.get());
      assertEquals(301, resp.statusCode());
      assertEquals("/otherpath", resp.request().path());
      testComplete();
    }));
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
    client.get(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/somepath")
      .setFollowRedirects(true)
      .setTimeout(500), onFailure(t -> {
      if (done.compareAndSet(false, true)) {
        assertEquals(2, redirections.get());
        testComplete();
      }
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
    startServer();
    HttpServer server2 = vertx.createHttpServer(options);
    server2.requestHandler(req -> {
      assertEquals(1, redirects.get());
      assertEquals(scheme + "://localhost:" + port + "/whatever", req.absoluteURI());
      req.response().end();
      complete();
    });
    startServer(server2);
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        assertEquals(scheme + "://localhost:" + port + "/whatever", resp.request().absoluteURI());
        complete();
      }))
      .setFollowRedirects(true)
      .setAuthority("localhost:" + options.getPort())
      .end();
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
      Promise<HttpClientRequest> fut = Promise.promise();
      vertx.setTimer(25, id -> {
        HttpClientRequest req = client.request(new RequestOptions().setAbsoluteURI(scheme + "://localhost:" + port + "/custom"));
        req.putHeader("foo", "foo_another");
        req.setAuthority("localhost:" + port);
        fut.complete(req);
      });
      return fut.future();
    });
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        assertEquals(scheme + "://localhost:" + port + "/custom", resp.request().absoluteURI());
        complete();
      }))
      .setFollowRedirects(true)
      .putHeader("foo", "foo_value")
      .setAuthority("localhost:" + options.getPort())
      .end();
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
      public Future<Void> write(Buffer data) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setWriteQueueMaxSize(int maxSize) { throw new UnsupportedOperationException(); }
      public HttpClientRequest drainHandler(Handler<Void> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest pause() { throw new UnsupportedOperationException(); }
      public HttpClientRequest resume() { throw new UnsupportedOperationException(); }
      public HttpClientRequest fetch(long amount) { throw new UnsupportedOperationException(); }
      public HttpClientRequest endHandler(Handler<Void> endHandler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setFollowRedirects(boolean followRedirects) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setMaxRedirects(int maxRedirects) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setChunked(boolean chunked) { throw new UnsupportedOperationException(); }
      public boolean isChunked() { return false; }
      public HttpMethod method() { return method; }
      public String absoluteURI() { return baseURI; }
      public String uri() { throw new UnsupportedOperationException(); }
      public String path() { throw new UnsupportedOperationException(); }
      public String query() { throw new UnsupportedOperationException(); }
      public HttpClientRequest setAuthority(String authority) { throw new UnsupportedOperationException(); }
      public String getAuthority() { throw new UnsupportedOperationException(); }
      public MultiMap headers() { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(String name, String value) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(CharSequence name, CharSequence value) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(String name, Iterable<String> values) { throw new UnsupportedOperationException(); }
      public HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) { throw new UnsupportedOperationException(); }
      public Future<Void> write(String chunk) { throw new UnsupportedOperationException(); }
      public Future<Void> write(String chunk, String enc) { throw new UnsupportedOperationException(); }
      public void write(Buffer data, Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
      public void write(String chunk, Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
      public void write(String chunk, String enc, Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest continueHandler(@Nullable Handler<Void> handler) { throw new UnsupportedOperationException(); }
      public Future<HttpVersion> sendHead() { throw new UnsupportedOperationException(); }
      public HttpClientRequest sendHead(Handler<AsyncResult<HttpVersion>> completionHandler) { throw new UnsupportedOperationException(); }
      public Future<Void> end(String chunk) { throw new UnsupportedOperationException(); }
      public Future<Void> end(String chunk, String enc) { throw new UnsupportedOperationException(); }
      public void end(String chunk, Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
      public void end(String chunk, String enc, Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
      public void end(Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
      public Future<Void> end() { throw new UnsupportedOperationException(); }
      public Future<Void> end(Buffer chunk) { throw new UnsupportedOperationException(); }
      public void end(Buffer chunk, Handler<AsyncResult<Void>> handler) { throw new UnsupportedOperationException(); }
      public HttpClientRequest setTimeout(long timeoutMs) { throw new UnsupportedOperationException(); }
      public HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) { throw new UnsupportedOperationException(); }
      public boolean reset(long code) { return false; }
      public HttpConnection connection() { throw new UnsupportedOperationException(); }
      public HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload) { throw new UnsupportedOperationException(); }
      public boolean writeQueueFull() { throw new UnsupportedOperationException(); }
      public HttpClientRequest setStreamPriority(StreamPriority streamPriority) { return this; }
      public StreamPriority getStreamPriority() { return null; }
      public HttpClientRequest onComplete(Handler<AsyncResult<HttpClientResponse>> handler) { throw new UnsupportedOperationException(); }
      public boolean isComplete() { throw new UnsupportedOperationException(); }
      public Handler<AsyncResult<HttpClientResponse>> getHandler() { throw new UnsupportedOperationException(); }
      public boolean tryComplete(HttpClientResponse result) { throw new UnsupportedOperationException(); }
      public boolean tryFail(Throwable cause) { throw new UnsupportedOperationException(); }
      public HttpClientResponse result() { throw new UnsupportedOperationException(); }
      public Throwable cause() { throw new UnsupportedOperationException(); }
      public boolean succeeded() { throw new UnsupportedOperationException(); }
      public boolean failed() { throw new UnsupportedOperationException(); }
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
      public HttpClientResponse streamPriorityHandler(Handler<StreamPriority> handler) { return this; }
      public HttpClientResponse bodyHandler(Handler<Buffer> bodyHandler) { throw new UnsupportedOperationException(); }
      public Future<Buffer> body() { throw new UnsupportedOperationException(); }
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

    client.get(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/first/call/from/client")
      .setFollowRedirects(true), onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      testComplete();
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
      client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/" + i)
        .setHandler(onSuccess(resp -> {
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
        })).end(TestUtils.randomAlphaString(256));
    }
    await();
  }

  @Test
  public void testEventHandlersNotHoldingLockOnClose() throws Exception {
    waitFor(7);
    server.requestHandler(req -> {
      HttpConnection conn = req.connection();
      req.exceptionHandler(err -> {
        assertFalse(Thread.holdsLock(conn));
        complete();
      });
      HttpServerResponse resp = req.response();
      resp.exceptionHandler(err -> {
        assertFalse(Thread.holdsLock(conn));
        complete();
      });
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
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        HttpConnection conn = resp.request().connection();
        resp.exceptionHandler(err -> {
          assertFalse(Thread.holdsLock(conn));
          complete();
        });
        conn.closeHandler(v -> {
          assertFalse(Thread.holdsLock(conn));
          complete();
        });
        conn.close();
      }));
    req.exceptionHandler(err -> {
      assertFalse(Thread.holdsLock(req.connection()));
      complete();
    });
    req.setChunked(true).sendHead();
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
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        resp.handler(v -> {
          resp.request().connection().close();
        });
      }))
      .end();
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
      resp.write(buff, onFailure(err1 -> {
        resp.write("foo", onFailure( err2 -> {
          resp.write("foo", "UTF-8", onFailure(err3 -> {
            resp.end(onFailure(err4 -> {
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
      resp.sendFile("webroot/somefile.html", onFailure(err -> {
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
    startServer();
    client.connectionHandler(clientConn::set);
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", onFailure(resp -> {
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
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        resp.endHandler(v -> {
          resp.request().connection().close();
        });
      }))
      .end();
    await();
  }

  private TestLoggerFactory testLogging() throws Exception {
    return TestUtils.testLogging(() -> {
       try {
        server.requestHandler(req -> {
          req.response().end();
        });
        startServer();
        client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
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
    startServer(testAddress);
    AtomicInteger exceptionCount = new AtomicInteger();
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setTryUseCompression(true));
    client.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        complete();
      });
    });
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(ar -> {
        if (ar.failed()) {
          complete();
        } else {
          HttpClientResponse resp = ar.result();
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
        }
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
    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertTrue(resp.headers().contains("Quux", "quux", false));
          assertFalse(resp.headers().contains("Quux", "quUx", false));
          testComplete();
        })).putHeader("foo", "foo")
        .end();
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
    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertTrue(resp.headers().contains("Quux", "quux", true));
          assertTrue(resp.headers().contains("Quux", "quUx", true));
          testComplete();
        }))
        .putHeader("foo", "foo")
        .end();
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
    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertTrue(resp.headers().contains(Quux, quux, false));
          assertFalse(resp.headers().contains(Quux, quUx, false));
          testComplete();
        })).putHeader(foo, foo)
        .end();
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
    server.listen(testAddress, onSuccess(server -> {
      client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertTrue(resp.headers().contains(Quux, quux, true));
          assertTrue(resp.headers().contains(Quux, quUx, true));
          testComplete();
        }))
        .putHeader(foo, foo)
        .end();
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
    client.request(HttpMethod.POST, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        resp.bodyHandler(buff -> {
          testComplete();
        });
      }))
      .exceptionHandler(this::fail)
      .putHeader("content-length", String.valueOf(length))
      .end(expected);
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
          AtomicBoolean f = new AtomicBoolean();
          client.request(new RequestOptions().setAbsoluteURI("http://invalid-host-name.foo.bar"))
            .setHandler(onFailure(resp -> {
              if (f.compareAndSet(false, true)) {
                if (failures.incrementAndGet() == poolSize + 1) {
                  testComplete();
                }
              }
            }))
            .end();
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
      client.request(HttpMethod.GET, testAddress, -1, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {}));
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
    HttpClientRequest req = client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(ar -> {});
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
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
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
      })).end();
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
      .setMaxPoolSize(1)
      .setKeepAliveTimeout(10)
    );
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
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
      }))
      .end();
    await();
  }

  @Test
  public void testHttpConnect() {
    Buffer buffer = TestUtils.randomBuffer(128);
    Buffer received = Buffer.buffer();
    CompletableFuture<Void> closeSocket = new CompletableFuture<>();
    vertx.createNetServer(new NetServerOptions().setPort(1235).setHost("localhost")).connectHandler(socket -> {
      socket.handler(socket::write);
      closeSocket.thenAccept(v -> {
        socket.close();
      });
    }).listen(onSuccess(netServer -> {
      server.requestHandler(req -> {
        vertx.createNetClient(new NetClientOptions()).connect(1235, "localhost", onSuccess(dst -> {

          req.response().setStatusCode(200);
          req.response().setStatusMessage("Connection established");

          // Now create a NetSocket
          NetSocket src = req.netSocket();

          // Create pumps which echo stuff
          Pump pump1 = Pump.pump(src, dst).start();
          Pump pump2 = Pump.pump(dst, src).start();
          dst.closeHandler(v -> {
            pump1.stop();
            pump2.stop();
            src.close();
          });
        }));
      });
      server.listen(testAddress, onSuccess(s -> {
        client.request(HttpMethod.CONNECT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
          .setHandler(onSuccess(resp -> {
            assertEquals(200, resp.statusCode());
          })).netSocket(onSuccess(socket -> {
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
        }))
          .end();
      }));
    }));

    await();
  }

  @Test
  public void testClientNetSocketConnectSuccess() {
    waitFor(3);

    server.requestHandler(req -> {
      req.response().headers().set("HTTP/1.1", "101 Upgrade");
      NetSocket so = req.netSocket();
      so.handler(buff -> {
        if (buff.toString().equals("ping")) {
          so.end(Buffer.buffer("pong"));
        }
      });
      so.endHandler(v -> {
        complete();
      });
    });

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.CONNECT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          complete();
        }));
      req.netSocket(onSuccess(so -> {
        Buffer received = Buffer.buffer();
        so.handler(received::appendBuffer);
        so.endHandler(v -> {
          assertEquals("pong", received.toString());
          complete();
        });
        so.write(Buffer.buffer("ping"));
      }));
      req.end();
    }));

    await();
  }

  @Test
  public void testClientNetSocketConnectReject() {
    waitFor(2);

    server.requestHandler(req -> {
      req.response().setStatusCode(404).end();
    });

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.CONNECT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(404, resp.statusCode());
          complete();
        }));
      req.netSocket(onFailure(err -> {
        complete();
      }));
      req.end();
    }));

    await();
  }

  @Test
  public void testClientNetSocketConnectFailure() {
    waitFor(2);

    server.requestHandler(req -> {
      req.connection().close();
    });

    server.listen(testAddress, onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.CONNECT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onFailure(err -> {
          complete();
        }));
      req.netSocket(onFailure(err -> {
        complete();
      }));
      req.end();
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
    client.close();
    server.listen(testAddress, onSuccess(s -> {
      client = vertx.createHttpClient(createBaseClientOptions());
      HttpClientRequest req = client.request(HttpMethod.CONNECT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
        }));
      req.netSocket(onSuccess(so -> {
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
      })).end();
    }));

    await();
  }

  @Test
  public void testServerNetSocketCloseWithHandler() {
    waitFor(3);
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.close(onSuccess(v -> {
        complete();
      }));
    });
    client.close();
    server.listen(onSuccess(s -> {
      client = vertx.createHttpClient(createBaseClientOptions());
      HttpClientRequest req = client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(onSuccess(resp -> {
        complete();
      }));
      req.netSocket(onSuccess(so -> {
        so.closeHandler(v -> {
          complete();
        });
      })).end();
    }));
    await();
  }

  @Test
  public void testClientNetSocketCloseWithHandler() {
    waitFor(3);
    server.requestHandler(req -> {
      NetSocket so = req.netSocket();
      so.closeHandler(v -> {
        complete();
      });
    });
    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.CONNECT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI).setHandler(onSuccess(resp -> {
        complete();
      }));
      req.netSocket(onSuccess(so -> {
        so.close(onSuccess(v -> {
          complete();
        }));
      })).end();
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
    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.CONNECT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          complete();
        })).end();
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
    server.listen(testAddress, onSuccess(s -> {
      client.request(HttpMethod.CONNECT, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
        .setHandler(onSuccess(resp -> {
          assertEquals(200, resp.statusCode());
          complete();
        }))
        .end();
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
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        complete();
      }))
      .end();

    await();
  }

  @Test
  public void testServerResponseWriteSuccess() throws Exception {
    testServerResponseWriteSuccess((resp, handler) -> resp.write(TestUtils.randomBuffer(1024), handler));
  }

  @Test
  public void testServerResponseEndSuccess() throws Exception {
    testServerResponseWriteSuccess((resp, handler) -> resp.end(TestUtils.randomBuffer(1024), handler));
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
    startServer();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, onSuccess(resp -> {
      complete();
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
        resp.write(chunk, ar1 -> {
          if (ar1.succeeded()) {
            task[0].run();
          } else {
            resp.end(ar2 -> {
              testComplete();
            });
          }
        });
      };
      task[0].run();
    });
    startServer();
    client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, onSuccess(resp -> {
      resp.request().connection().close();
    }));
    await();
  }

  @Test
  public void testClientRequestWriteSuccess() throws Exception {
    testClientRequestWriteSuccess((req, handler) -> {
      req.setChunked(true);
      req.write(TestUtils.randomBuffer(1024), handler);
      req.end();
    });
  }

  @Test
  public void testClientRequestEnd1Success() throws Exception {
    testClientRequestWriteSuccess((req, handler) -> req.end(TestUtils.randomBuffer(1024), handler));
  }

  @Test
  public void testClientRequestEnd2Success() throws Exception {
    testClientRequestWriteSuccess(HttpClientRequest::end);
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
    startServer();
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
      complete();
    }));
    op.accept(req, onSuccess(v -> {
      complete();
    }));
    await();
  }

  @Test
  public void testClientRequestLazyWriteSuccess() throws Exception {
    testClientRequestLazyWriteSuccess((resp, handler) -> resp.write(TestUtils.randomBuffer(1024), handler));
  }

  @Test
  public void testClientRequestLazyEndSuccess() throws Exception {
    testServerResponseWriteSuccess((resp, handler) -> resp.end(TestUtils.randomBuffer(1024), handler));
  }

  private void testClientRequestLazyWriteSuccess(BiConsumer<HttpClientRequest, Handler<AsyncResult<Void>>> op) throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().end();
    });
    startServer();
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onSuccess(resp -> {
      complete();
    }))
      .setChunked(true);
    op.accept(req, onSuccess(v -> {
      complete();
    }));
    await();
  }

  @Test
  public void testClientResponseWriteFailure() throws Exception {
    server.requestHandler(req -> {
      req.connection().close();
    });
    startServer();
    HttpClientRequest req = client.request(HttpMethod.PUT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI)
      .setHandler(onFailure(err -> {
      }))
      .setChunked(true);
    Buffer chunk = randomBuffer(1024);
    Runnable[] task = new Runnable[1];
    task[0] = () -> {
      req.write(chunk, ar1 -> {
        if (ar1.succeeded()) {
          task[0].run();
        } else {
          req.end(ar2 -> {
            testComplete();
          });
        }
      });
    };
    task[0].run();
    await();
  }

  @Test
  public void testServerRequestBodyFuture() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1024));
    server.requestHandler(req -> {
      req.body(onSuccess(body -> {
        assertEquals(expected, body);
        req.response().end();
      }));
    });
    startServer(testAddress);
    client.request(
      testAddress,
      new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI(DEFAULT_TEST_URI))
      .setHandler(onSuccess(resp -> {
        testComplete();
      }))
      .end(expected);
    await();
  }

  @Test
  public void testServerRequestBodyFutureFail() throws Exception {
    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1024));
    server.requestHandler(req -> {
      req.body(onFailure(err -> {
        testComplete();
      }));
    });
    startServer(testAddress);
    HttpClientRequest req = client.request(
      testAddress,
      new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI(DEFAULT_TEST_URI)).setHandler(ar -> {

    }).setChunked(true);
    client.connectionHandler(conn -> {
      vertx.setTimer(100, id -> {
        req.reset();
      });
    });
    req.write(expected);
    await();
  }

  @Test
  public void testResetClientRequestBeforeActualSend() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
    });
    startServer(testAddress);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      client.request(
        testAddress,
        new RequestOptions()
          .setPort(DEFAULT_HTTP_PORT)
          .setHost(DEFAULT_HTTP_HOST)
          .setURI(DEFAULT_TEST_URI))
        .setHandler(onFailure(err -> {
          assertTrue(err instanceof StreamResetException);
          complete();
        }))
        .exceptionHandler(err -> {
          assertTrue(err instanceof StreamResetException);
          complete();
        })
        .sendHead(version -> fail())
        .reset();
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
      HttpClientRequest req = client.request(
        testAddress,
        new RequestOptions()
          .setPort(DEFAULT_HTTP_PORT)
          .setHost(DEFAULT_HTTP_HOST)
          .setURI(DEFAULT_TEST_URI))
        .setHandler(onFailure(err -> {
          complete();
        }))
        .exceptionHandler(err -> {
          complete();
        });
      req.sendHead(version -> {
        req.reset(0);
      });
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
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      HttpClientRequest req = client.request(
        testAddress,
        new RequestOptions()
          .setPort(DEFAULT_HTTP_PORT)
          .setHost(DEFAULT_HTTP_HOST)
          .setURI(DEFAULT_TEST_URI))
        .setHandler(onFailure(err -> {
          complete();
        }))
        .exceptionHandler(err -> {
          fail();
        });
      req.end();
      fut.thenAccept(v2 -> {
        ctx.runOnContext(v3 -> {
          req.reset(0);
        });
      });
    });
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
    assertEquals("foo=bar; Path=/somepath; Domain=foo.com; Secure", cookie.encode());
    cookie.setHttpOnly(true);
    assertEquals("foo=bar; Path=/somepath; Domain=foo.com; Secure; HTTPOnly", cookie.encode());
  }

  @Test
  public void testCookieSameSiteFieldEncoding() throws Exception {
    Cookie cookie = Cookie.cookie("foo", "bar").setSameSite(CookieSameSite.LAX);
    assertEquals("foo", cookie.getName());
    assertEquals("bar", cookie.getValue());
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
      assertEquals("bar", removed.getValue());
      req.response().end();
    }, resp -> {
      List<String> cookies = resp.headers().getAll("set-cookie");
      // the expired cookie must be sent back
      assertEquals(1, cookies.size());
      assertTrue(cookies.get(0).contains("foo=bar"));
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

  private void testCookies(String cookieHeader, Consumer<HttpServerRequest> serverChecker, Consumer<HttpClientResponse> clientChecker) throws Exception {
    server.requestHandler(serverChecker::accept);
    startServer(testAddress);
    client.request(
      testAddress,
      new RequestOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI(DEFAULT_TEST_URI)).setHandler(onSuccess(resp -> {
      clientChecker.accept(resp);
      testComplete();
    }))
      .putHeader(HttpHeaders.COOKIE.toString(), cookieHeader)
      .end();
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
      HttpClientRequest req = client.request(
        testAddress,
        new RequestOptions()
          .setPort(DEFAULT_HTTP_PORT)
          .setHost(DEFAULT_HTTP_HOST)
          .setURI(DEFAULT_TEST_URI))
        .setHandler(onSuccess(resp -> {
          complete();
        }));
      reqFut.complete(req);
    });
    HttpClientRequest req = reqFut.get(10, TimeUnit.SECONDS);
    Future<Void> endFut = req.end("msg");
    waitUntil(endFut::succeeded);
    // Set the handler after the future
    endFut.setHandler(onSuccess(v -> {
      assertNotNull(Vertx.currentContext());
      assertSameEventLoop(ctx, Vertx.currentContext());
      complete();
    }));
    await();
  }

  @Test
  public void testClientRequestWithLargeBodyInSmallChunks() throws Exception {
    testClientRequestWithLargeBodyInSmallChunks(false);
  }

  @Test
  public void testClientRequestWithLargeBodyInSmallChunksChunked() throws Exception {
    testClientRequestWithLargeBodyInSmallChunks(true);
  }

  private void testClientRequestWithLargeBodyInSmallChunks(boolean chunked) throws Exception {
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
      assertEquals(chunked & req.version() != HttpVersion.HTTP_2 ? HttpHeaders.CHUNKED.toString() : null, req.getHeader(HttpHeaders.TRANSFER_ENCODING));
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
    if (!chunked) {
      options.addHeader(HttpHeaders.CONTENT_LENGTH, contentLength);
    }
    client.send(options, src, onSuccess(resp -> {
      assertEquals(200, resp.statusCode());
      complete();
    }));
    await();
  }
}
