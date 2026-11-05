/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.http.HttpMethod.HEAD;
import static io.vertx.test.core.AssertExpectations.that;
import static io.vertx.test.core.TestUtils.onSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * Tests of the HTTP semantics the server must implement regardless of the protocol version.
 */
public abstract class HttpSemanticsTest extends SimpleHttpTest2 {

  protected HttpSemanticsTest(HttpConfigurator config) {
    super(config);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2
  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
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
    String contentLength = client.request(new RequestOptions(requestOptions).setMethod(HEAD))
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp.end().map(resp.headers().get("Content-Length"))))
      .await();
    assertEquals("41", contentLength);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
  @Test
  public void testHeadDoesNotSetAutomaticallySetContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.HEAD, 200, HttpHeaders.headers());
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
  @Test
  public void testHeadAllowsContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.HEAD, 200, HttpHeaders.set("content-length", "34"));
    assertEquals("34", respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9112#section-6.1
  @Test
  public void testHeadRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.HEAD, 200, HttpHeaders.set("transfer-encoding", "chunked"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
  @Test
  public void testNoContentRemovesContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 204, HttpHeaders.set("content-length", "34"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9112#section-6.1
  @Test
  public void testNoContentRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 204, HttpHeaders.set("transfer-encoding", "chunked"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-15.3.6
  @Test
  public void testResetContentSetsContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 205, HttpHeaders.headers());
    assertEquals("0", respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-15.3.6
  // https://www.rfc-editor.org/rfc/rfc9112#section-6.1
  @Test
  public void testResetContentRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 205, HttpHeaders.set("transfer-encoding", "chunked"));
    assertEquals("0", respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-15.4.5
  @Test
  public void testNotModifiedDoesNotSetAutomaticallySetContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 304, HttpHeaders.headers());
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
  @Test
  public void testNotModifiedAllowsContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 304, HttpHeaders.set("content-length", "34"));
    assertEquals("34", respHeaders.get("Content-Length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9112#section-6.1
  @Test
  public void testNotModifiedRemovesTransferEncodingHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 304, HttpHeaders.set("transfer-encoding", "chunked"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
  @Test
  public void test1xxRemovesContentLengthHeader() throws Exception {
    MultiMap respHeaders = checkEmptyHttpResponse(HttpMethod.GET, 102, HttpHeaders.set("content-length", "34"));
    assertNull(respHeaders.get("content-length"));
    assertNull(respHeaders.get("transfer-encoding"));
  }

  // https://www.rfc-editor.org/rfc/rfc9112#section-6.1
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
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
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
      .await();
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
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
      .await();
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2
  @Test
  public void testHeadResponseBodySuppressed() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.HEAD, req.method());
      req.response().write("HEL");
      req.response().end("LO");
    });
    startServer(testAddress);
    checkSuppressedResponseBody(HEAD, 200);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2
  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
  @Test
  public void testHeadResponseBodySuppressedWithContentLength() throws Exception {
    server.requestHandler(req -> {
      // The content-length advertises what a GET response would carry, the body itself is not sent
      req.response().putHeader(HttpHeaders.CONTENT_LENGTH, "5").end("HELLO");
    });
    startServer(testAddress);
    HttpClientResponse resp = checkSuppressedResponseBody(HEAD, 200);
    assertEquals("5", resp.headers().get(HttpHeaders.CONTENT_LENGTH));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2
  @Test
  public void testHeadResponseBodyWriteAcknowledged(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    server.requestHandler(req -> {
      req.response().write("HEL").onComplete(onSuccess(v -> checkpoint1.succeed()));
      req.response().end("LO").onComplete(onSuccess(v -> checkpoint2.succeed()));
    });
    startServer(testAddress);
    checkSuppressedResponseBody(HEAD, 200);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-15.3.5
  @Test
  public void test204ResponseBodySuppressed() throws Exception {
    server.requestHandler(req -> req.response().setStatusCode(204).end("HELLO"));
    startServer(testAddress);
    checkSuppressedResponseBody(HttpMethod.GET, 204);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-15.3.6
  @Test
  public void test205ResponseBodySuppressed() throws Exception {
    server.requestHandler(req -> req.response().setStatusCode(205).end("HELLO"));
    startServer(testAddress);
    checkSuppressedResponseBody(HttpMethod.GET, 205);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-15.4.5
  // https://www.rfc-editor.org/rfc/rfc9110#section-8.6
  @Test
  public void test304ResponseBodySuppressedWithContentLength() throws Exception {
    server.requestHandler(req -> req.response()
      .setStatusCode(304)
      // The content-length advertises what a 200 response would carry, the body itself is not sent
      .putHeader(HttpHeaders.CONTENT_LENGTH, "5")
      .end("HELLO"));
    startServer(testAddress);
    HttpClientResponse resp = checkSuppressedResponseBody(HttpMethod.GET, 304);
    assertEquals("5", resp.headers().get(HttpHeaders.CONTENT_LENGTH));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2
  @Test
  public void testHeadResponseSendFileSuppressed(Checkpoint checkpoint) throws Exception {
    File file = setupFile("head-sendfile.txt", "HELLO HTTP SEMANTICS");
    server.requestHandler(req -> req.response()
      .sendFile(file.getAbsolutePath())
      .onComplete(onSuccess(v -> checkpoint.succeed())));
    startServer(testAddress);
    checkSuppressedResponseBody(HEAD, 200);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2
  // https://www.rfc-editor.org/rfc/rfc9110#section-6.5
  @Test
  public void testHeadResponseTrailersSuppressed() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.putTrailer("x-trailer", "value");
      resp.end("HELLO");
    });
    startServer(testAddress);
    assertEquals(0, checkSuppressedResponseBody(HEAD, 200).trailers().size());
  }

  private HttpClientResponse checkSuppressedResponseBody(HttpMethod method, int sc) throws Exception {
    return client.request(new RequestOptions(requestOptions).setMethod(method))
      .compose(req -> req
        .setFollowRedirects(false)
        .send()
        .expecting(that(resp -> assertEquals(sc, resp.statusCode())))
        .compose(resp -> resp
          .body()
          .expecting(that(body -> assertEquals(0, body.length())))
          .map(body -> resp)))
      .await(20, TimeUnit.SECONDS);
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-7.6.1
  // https://www.rfc-editor.org/rfc/rfc9113#section-8.2.2
  // https://www.rfc-editor.org/rfc/rfc9114#section-4.2
  @Test
  public void testConnectionHeadersSanitized() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse resp = req.response();
      resp.putHeader(HttpHeaders.CONNECTION, HttpHeaders.CLOSE);
      resp.putHeader(HttpHeaders.KEEP_ALIVE, "timeout=5");
      resp.putHeader(HttpHeaders.UPGRADE, "websocket");
      resp.putHeader("proxy-connection", "keep-alive");
      resp.end("hello");
    });
    startServer(testAddress);
    MultiMap respHeaders = client.request(new RequestOptions(requestOptions))
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp
          .body()
          .expecting(that(body -> assertEquals("hello", body.toString())))
          .map(body -> resp.headers())))
      .await(20, TimeUnit.SECONDS);
    assertConnectionHeadersSanitized(respHeaders);
  }

  // Connection-specific headers are forbidden on the wire for HTTP/2 and HTTP/3,
  // overridden for HTTP/1.x where they are meaningful
  protected void assertConnectionHeadersSanitized(MultiMap respHeaders) {
    assertNull(respHeaders.get(HttpHeaders.CONNECTION));
    assertNull(respHeaders.get(HttpHeaders.KEEP_ALIVE));
    assertNull(respHeaders.get(HttpHeaders.UPGRADE));
    assertNull(respHeaders.get("proxy-connection"));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#section-7.2
  @Test
  public void testMissingAuthority() throws Exception {
    server.requestHandler(req -> {
      assertNull(req.authority());
      assertFalse(((HttpServerRequestInternal) req).isValidAuthority());
      req.response().end();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(request -> request
        .authority(null)
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
      .await();
  }
}
