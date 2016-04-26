/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HeadersAdaptor;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.WorkerContext;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertIllegalStateException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

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
    HttpClientRequest req;
    if (absolute) {
      req = client.requestAbs(method, "http://" + DEFAULT_HTTP_HOST + ":" + DEFAULT_HTTP_PORT + uri, handler);
    } else {
      req = client.request(method, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, uri, handler);
    }
    testSimpleRequest(uri, method, req);
  }

  private void testSimpleRequest(String uri, HttpMethod method, HttpClientRequest request) {
    int index = uri.indexOf('?');
    String path = index == -1 ? uri : uri.substring(0, index);
    String query = index == -1 ? null : uri.substring(index + 1);
    server.requestHandler(req -> {
      String expectedPath = req.method() == HttpMethod.CONNECT && req.version() == HttpVersion.HTTP_2 ? null : path;
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
    testParamDecoding("äüö");
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
    testParamDecoding("äüö+% hello");
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
    Buffer body = TestUtils.randomBuffer(1000);

    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        assertEquals(body, buff);
        testComplete();
      });
    });

    server.listen(onSuccess(s -> {
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler());
      req.setChunked(true);
      req.write(body);
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
  public void testUseResponseAfterComplete() {
    server.requestHandler(req -> {
      Buffer buff = Buffer.buffer();
      HttpServerResponse resp = req.response();

      assertFalse(resp.ended());
      resp.end();
      assertTrue(resp.ended());

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

      testComplete();
    });

    server.listen(onSuccess(s -> {
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, noOpHandler()).end();
    }));

    await();
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

    server.listen();
    assertIllegalStateException(() -> server.requestHandler(noOpHandler()));
    assertIllegalStateException(() -> server.websocketHandler(noOpHandler()));
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
    server.listen();
    assertIllegalStateException(() -> server.listen());
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
            upload.handler(buffer -> tot.appendBuffer(buffer));
            uploadedFileName = null;
          } else {
            uploadedFileName = new File(testDir, UUID.randomUUID().toString()).getPath();
            upload.streamToFileSystem(uploadedFileName);
          }
          upload.endHandler(v -> {
            if (streamToDisk) {
              Buffer uploaded = vertx.fileSystem().readFileBlocking(uploadedFileName);
              assertEquals(content, uploaded);
            } else {
              assertEquals(content, tot);
            }
            assertTrue(upload.isSizeAvailable());
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
      HttpClientRequest req = client.request(HttpMethod.POST, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/form", resp -> {
        // assert the response
        assertEquals(200, resp.statusCode());
        resp.bodyHandler(body -> {
          assertEquals(0, body.length());
        });
        assertEquals(0, attributeCount.get());
        testComplete();
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
          assertTrue(ctx instanceof WorkerContext);
        } else {
          assertTrue(ctx instanceof EventLoopContext);
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
    server.requestHandler(req -> req.response().setStatusCode(200).end());
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
        } catch (Exception e) {
          e.printStackTrace();
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
  public void testSetHandlersOnEnd() {
    String path = "/some/path";
    server.requestHandler(req -> req.response().setStatusCode(200).end());
    server.listen(ar -> {
      assertTrue(ar.succeeded());
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
}
