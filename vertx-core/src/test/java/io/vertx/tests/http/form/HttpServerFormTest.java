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
package io.vertx.tests.http.form;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Assert;
import org.junit.Test;

import java.net.URLEncoder;

import static org.junit.Assert.assertEquals;

public abstract class HttpServerFormTest extends SimpleHttpTest2 {

  public HttpServerFormTest(HttpConfigurator config) {
    super(config);
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
      .await();
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
        Assert.fail();
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
      .await();
  }
}
