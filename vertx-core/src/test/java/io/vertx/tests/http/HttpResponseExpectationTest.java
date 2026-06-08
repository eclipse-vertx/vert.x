/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.Expectation;
import io.vertx.core.http.*;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpTestBase2;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class HttpResponseExpectationTest extends HttpTestBase2 {

  @Test
  public void testExpectFail() throws Exception {
    assertNotNull(testExpectation(
      resp -> false,
      HttpServerResponse::end)
    );
  }

  @Test
  public void testExpectPass() throws Exception {
    assertNull(testExpectation(
      resp -> true,
      HttpServerResponse::end));
  }

  @Test
  public void testExpectStatusFail() throws Exception {
    assertNotNull(testExpectation(
      HttpResponseExpectation.status(200),
      resp -> resp.setStatusCode(201).end()));
  }

  @Test
  public void testExpectStatusPass() throws Exception {
    assertNull(testExpectation(
      HttpResponseExpectation.status(200),
      resp -> resp.setStatusCode(200).end()));
  }

  @Test
  public void testExpectStatusRangeFail() throws Exception {
    assertNotNull(testExpectation(
      HttpResponseExpectation.SC_SUCCESS,
      resp -> resp.setStatusCode(500).end()));
  }

  @Test
  public void testExpectStatusRangePass1() throws Exception {
    assertNull(testExpectation(
      HttpResponseExpectation.SC_SUCCESS,
      resp -> resp.setStatusCode(200).end()));
  }

  @Test
  public void testExpectStatusRangePass2() throws Exception {
    assertNull(testExpectation(
      HttpResponseExpectation.SC_SUCCESS,
      resp -> resp.setStatusCode(299).end()));
  }

  @Test
  public void testExpectContentTypeFail() throws Exception {
    assertNotNull(testExpectation(
      HttpResponseExpectation.JSON,
      HttpServerResponse::end));
  }

  @Test
  public void testExpectOneOfContentTypesFail() throws Exception {
    assertNotNull(testExpectation(
      HttpResponseExpectation.contentType(Arrays.asList("text/plain", "text/csv")),
      httpServerResponse -> httpServerResponse.putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML).end()));
  }

  @Test
  public void testExpectContentTypePass() throws Exception {
    assertNull(testExpectation(
      HttpResponseExpectation.JSON,
      resp -> resp.putHeader("content-type", "application/JSON").end()));
  }

  @Test
  public void testExpectContentTypeWithEncodingPass() throws Exception {
    assertNull(testExpectation(
      HttpResponseExpectation.JSON,
      resp -> resp.putHeader("content-type", "application/JSON;charset=UTF-8").end()));
  }

  @Test
  public void testExpectOneOfContentTypesPass() throws Exception {
    assertNull(testExpectation(
      HttpResponseExpectation.contentType(Arrays.asList("text/plain", "text/HTML")),
      httpServerResponse -> httpServerResponse.putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML).end()));
  }

  @Test
  public void testExpectCustomException() throws Exception {
    Expectation<HttpResponseHead> predicate = ((Expectation<HttpResponseHead>) value -> false)
      .wrappingFailure((r,e) -> new CustomException("boom"));
    Throwable cause = testExpectation(predicate, HttpServerResponse::end);
    Assertions
      .assertThat(cause)
      .isNotNull()
      .isInstanceOf(CustomException.class);
    CustomException customException = (CustomException) cause;
    assertEquals("boom", customException.getMessage());
  }

/*
  @Test
  public void testExpectCustomExceptionWithResponseBody() throws Exception {
    UUID uuid = UUID.randomUUID();

    ResponsePredicate predicate = ResponsePredicate.create(ResponsePredicate.SC_SUCCESS, ErrorConverter.createFullBody(result -> {
      JsonObject body = result.response().bodyAsJsonObject();
      return new CustomException(UUID.fromString(body.getString("tag")), body.getString("message"));
    }));

    testExpectation(true, req -> req.expect(predicate), httpServerResponse -> {
      httpServerResponse
        .setStatusCode(400)
        .end(new JsonObject().put("tag", uuid.toString()).put("message", "tilt").toBuffer());
    }, ar -> {
      Throwable cause = ar.cause();
      assertThat(cause, a -> a.isInstanceOf(CustomException.class));
      CustomException customException = (CustomException) cause;
      assertEquals("tilt", customException.getMessage());
      assertEquals(uuid, customException.tag);
    });
  }
*/

  @Test
  public void testExpectCustomExceptionWithStatusCode() throws Exception {
    UUID uuid = UUID.randomUUID();
    int statusCode = 400;

    Expectation<HttpResponseHead> predicate = HttpResponseExpectation.SC_SUCCESS
      .wrappingFailure((value, err) -> new CustomException(uuid, String.valueOf(value.statusCode())));

    Throwable cause = testExpectation(predicate, httpServerResponse -> {
      httpServerResponse
        .setStatusCode(statusCode)
        .end(TestUtils.randomBuffer(2048));
    });
    Assertions
      .assertThat(cause)
      .isNotNull()
      .isInstanceOf(CustomException.class);
    CustomException customException = (CustomException) cause;
    assertEquals(String.valueOf(statusCode), customException.getMessage());
    assertEquals(uuid, customException.tag);
  }

  @Test
  public void testExpectFunctionThrowsException() throws Exception {
    Expectation<HttpResponseHead> predicate = ((Expectation<HttpResponseHead>) value -> false)
      .wrappingFailure((v,e) -> {
        throw new IndexOutOfBoundsException("boom");
      });

    Throwable cause = testExpectation(predicate, HttpServerResponse::end);
    Assertions
      .assertThat(cause)
      .isNotNull()
      .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  public void testErrorConverterReturnsNull() throws Exception {
    Expectation<HttpResponseHead> predicate = ((Expectation<HttpResponseHead>) value -> false)
      .wrappingFailure((v,e) -> null);

    Throwable cause = testExpectation(predicate, HttpServerResponse::end);
    Assertions
      .assertThat(cause)
      .isNotNull()
      .isNotInstanceOf(NullPointerException.class);
  }

  private static class CustomException extends Exception {

    UUID tag;

    CustomException(String message) {
      super(message);
    }

    CustomException(UUID tag, String message) {
      super(message);
      this.tag = tag;
    }
  }

  private Throwable testExpectation(Expectation<HttpResponseHead> expectation,
                                    Consumer<HttpServerResponse> server) throws Exception {
    this.server.requestHandler(request -> server.accept(request.response()));
    startServer();
    try {
      client
        .request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/test")
        .compose(request -> request
          .send()
          .expecting(expectation))
        .await();
      return null;
    } catch (Exception e) {
      return e;
    }
  }
}
