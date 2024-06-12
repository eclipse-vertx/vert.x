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
package io.vertx.core.http;

import io.vertx.core.AsyncResult;
import io.vertx.core.Expectation;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;

public class HttpResponseExpectationTest extends HttpTestBase {

  @Test
  public void testExpectFail() throws Exception {
    testExpectation(true,
      resp -> false,
      HttpServerResponse::end);
  }

  @Test
  public void testExpectPass() throws Exception {
    testExpectation(false,
      resp -> true,
      HttpServerResponse::end);
  }

  @Test
  public void testExpectStatusFail() throws Exception {
    testExpectation(true,
      HttpResponseExpectation.status(200),
      resp -> resp.setStatusCode(201).end());
  }

  @Test
  public void testExpectStatusPass() throws Exception {
    testExpectation(false,
      HttpResponseExpectation.status(200),
      resp -> resp.setStatusCode(200).end());
  }

  @Test
  public void testExpectStatusRangeFail() throws Exception {
    testExpectation(true,
      HttpResponseExpectation.SC_SUCCESS,
      resp -> resp.setStatusCode(500).end());
  }

  @Test
  public void testExpectStatusRangePass1() throws Exception {
    testExpectation(false,
      HttpResponseExpectation.SC_SUCCESS,
      resp -> resp.setStatusCode(200).end());
  }

  @Test
  public void testExpectStatusRangePass2() throws Exception {
    testExpectation(false,
      HttpResponseExpectation.SC_SUCCESS,
      resp -> resp.setStatusCode(299).end());
  }

  @Test
  public void testExpectContentTypeFail() throws Exception {
    testExpectation(true,
      HttpResponseExpectation.JSON,
      HttpServerResponse::end);
  }

  @Test
  public void testExpectOneOfContentTypesFail() throws Exception {
    testExpectation(true,
      HttpResponseExpectation.contentType(Arrays.asList("text/plain", "text/csv")),
      httpServerResponse -> httpServerResponse.putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML).end());
  }

  @Test
  public void testExpectContentTypePass() throws Exception {
    testExpectation(false,
      HttpResponseExpectation.JSON,
      resp -> resp.putHeader("content-type", "application/JSON").end());
  }

  @Test
  public void testExpectContentTypeWithEncodingPass() throws Exception {
    testExpectation(false,
      HttpResponseExpectation.JSON,
      resp -> resp.putHeader("content-type", "application/JSON;charset=UTF-8").end());
  }

  @Test
  public void testExpectOneOfContentTypesPass() throws Exception {
    testExpectation(false,
      HttpResponseExpectation.contentType(Arrays.asList("text/plain", "text/HTML")),
      httpServerResponse -> httpServerResponse.putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML).end());
  }

  @Test
  public void testExpectCustomException() throws Exception {
    Expectation<HttpResponseHead> predicate = ((Expectation<HttpResponseHead>) value -> false)
      .wrappingFailure((r,e) -> new CustomException("boom"));

    testExpectation(true, predicate, HttpServerResponse::end, ar -> {
      Throwable cause = ar.cause();
      assertThat(cause, instanceOf(CustomException.class));
      CustomException customException = (CustomException) cause;
      assertEquals("boom", customException.getMessage());
    });
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
      assertThat(cause, instanceOf(CustomException.class));
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

    testExpectation(true, predicate, httpServerResponse -> {
      httpServerResponse
        .setStatusCode(statusCode)
        .end(TestUtils.randomBuffer(2048));
    }, ar -> {
      Throwable cause = ar.cause();
      assertThat(cause, instanceOf(CustomException.class));
      CustomException customException = (CustomException) cause;
      assertEquals(String.valueOf(statusCode), customException.getMessage());
      assertEquals(uuid, customException.tag);
    });
  }

  @Test
  public void testExpectFunctionThrowsException() throws Exception {
    Expectation<HttpResponseHead> predicate = ((Expectation<HttpResponseHead>) value -> false)
      .wrappingFailure((v,e) -> {
        throw new IndexOutOfBoundsException("boom");
      });

    testExpectation(true, predicate, HttpServerResponse::end, ar -> {
      assertThat(ar.cause(), instanceOf(IndexOutOfBoundsException.class));
    });
  }

  @Test
  public void testErrorConverterReturnsNull() throws Exception {
    Expectation<HttpResponseHead> predicate = ((Expectation<HttpResponseHead>) value -> false)
      .wrappingFailure((v,e) -> null);

    testExpectation(true, predicate, HttpServerResponse::end, ar -> {
      assertThat(ar.cause(), not(instanceOf(NullPointerException.class)));
    });
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

  private void testExpectation(boolean shouldFail,
                               Expectation<HttpResponseHead> expectation,
                               Consumer<HttpServerResponse> server) throws Exception {
    testExpectation(shouldFail, expectation, server, null);
  }

  private void testExpectation(boolean shouldFail,
                               Expectation<HttpResponseHead> expectation,
                               Consumer<HttpServerResponse> server,
                               Consumer<AsyncResult<?>> resultTest) throws Exception {
    this.server.requestHandler(request -> server.accept(request.response()));
    startServer();
    client
      .request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/test")
      .compose(request -> request
        .send()
        .expecting(expectation))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          assertFalse("Expected response success", shouldFail);
        } else {
          assertTrue("Expected response failure", shouldFail);
        }
        if (resultTest != null) resultTest.accept(ar);
        testComplete();
      });
    await();
  }
}
