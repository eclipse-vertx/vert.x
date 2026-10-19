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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Assert;
import org.junit.Test;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.assertWaitUntil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    sendAndAwait(new RequestOptions(requestOptions).setMethod(method), req -> {
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

  // %zz is not a valid percent encoding
  private static final String MALFORMED_URL_ENCODED_FORM = "a=%zz";

  private List<String> recordFormEvents(HttpServerRequest req) {
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    req.setExpectMultipart(true);
    req.exceptionHandler(err -> events.add("request-exception"));
    req.response().exceptionHandler(err -> events.add("response-exception"));
    return events;
  }

  private int sendForm(String contentType, Buffer body, int chunkSize) throws Exception {
    return client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
        if (chunkSize > 0) {
          req.setChunked(true);
          for (int i = 0;i < body.length();i += chunkSize) {
            req.write(body.slice(i, Math.min(body.length(), i + chunkSize)));
          }
          return req.end().compose(v -> req.response());
        } else {
          return req.send(body);
        }
      })
      .compose(resp -> resp.end().map(v -> resp.statusCode()))
      .await(10, TimeUnit.SECONDS);
  }

  @Test
  public void testMalformedUrlEncodedForm() throws Exception {
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(req -> {
      List<String> reqEvents = recordFormEvents(req);
      req.endHandler(v -> {
        events.addAll(reqEvents);
        events.add("end:" + req.formAttributes().size());
        req.response().setStatusCode(400).end();
      });
    });
    startServer(testAddress);
    assertEquals(400, sendForm(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString(), Buffer.buffer(MALFORMED_URL_ENCODED_FORM), 0));
    assertWaitUntil(() -> events.stream().anyMatch(e -> e.startsWith("end:")));
    assertEquals(1, Collections.frequency(events, "request-exception"));
    assertEquals(1, Collections.frequency(events, "response-exception"));
    assertTrue(events.indexOf("request-exception") < events.indexOf("end:0"));
  }

  @Test
  public void testFormWithSeveralErrorsReportsTheFirstOnly() throws Exception {
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(req -> {
      List<String> reqEvents = recordFormEvents(req);
      req.endHandler(v -> {
        events.addAll(reqEvents);
        events.add("end");
        req.response().setStatusCode(400).end();
      });
    });
    startServer(testAddress);
    assertEquals(400, sendForm(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString(), Buffer.buffer("a=%zz&b=%zz&c=%zz"), 0));
    assertWaitUntil(() -> events.contains("end"));
    assertEquals(1, Collections.frequency(events, "request-exception"));
    assertTrue(events.indexOf("request-exception") < events.indexOf("end"));
  }

  @Test
  public void testFormDecodingErrorKeepsDeliveringChunks() throws Exception {
    StringBuilder sb = new StringBuilder();
    for (int i = 0;i < 300;i++) {
      sb.append(i > 0 ? "&" : "").append("a").append(i).append("=b");
    }
    Buffer body = Buffer.buffer(sb.toString());
    Buffer received = Buffer.buffer();
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(req -> {
      List<String> reqEvents = recordFormEvents(req);
      req.handler(received::appendBuffer);
      req.endHandler(v -> {
        events.addAll(reqEvents);
        events.add("end");
        req.response().setStatusCode(400).end();
      });
    });
    startServer(testAddress);
    assertEquals(400, sendForm(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString(), body, 64));
    assertWaitUntil(() -> events.contains("end"));
    assertEquals(1, Collections.frequency(events, "request-exception"));
    assertTrue(events.indexOf("request-exception") < events.indexOf("end"));
    assertEquals(body, received);
  }

  @Test
  public void testFormErrorFailsEndAndBodyFutures() throws Exception {
    AtomicReference<Future<Buffer>> body = new AtomicReference<>();
    AtomicReference<Future<Void>> end = new AtomicReference<>();
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      body.set(req.body());
      end.set(req.end());
      end.get().onComplete(ar -> req.response().setStatusCode(ar.succeeded() ? 200 : 400).end());
    });
    startServer(testAddress);
    assertEquals(400, sendForm(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString(), Buffer.buffer(MALFORMED_URL_ENCODED_FORM), 0));
    assertWaitUntil(() -> body.get().isComplete());
    assertTrue(body.get().failed());
    assertTrue(end.get().failed());
    assertEquals(end.get().cause(), body.get().cause());
  }

  @Test
  public void testFormErrorFailsEndFutureRequestedAfterTheError() throws Exception {
    StringBuilder sb = new StringBuilder();
    for (int i = 0;i < 300;i++) {
      sb.append(i > 0 ? "&" : "").append("a").append(i).append("=b");
    }
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicReference<Future<Void>> end = new AtomicReference<>();
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.exceptionHandler(failure::set);
      req.handler(chunk -> {
        if (failure.get() != null && end.get() == null) {
          end.set(req.end());
        }
      });
      req.endHandler(v -> req.response().setStatusCode(400).end());
    });
    startServer(testAddress);
    assertEquals(400, sendForm(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString(), Buffer.buffer(sb.toString()), 64));
    assertWaitUntil(() -> end.get() != null && end.get().isComplete());
    assertTrue("end() requested after a form error should fail", end.get().failed());
  }

  @Test
  public void testTruncatedMultipartFailsUploadInProgress() throws Exception {
    String boundary = "a4e41223-a527-49b6-ac1c-315d76be757e";
    String body = "--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
      "Content-Type: text/plain\r\n" +
      "\r\n" +
      "some-content-without-the-closing-boundary";
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.exceptionHandler(err -> events.add("request-exception"));
      req.uploadHandler(upload -> {
        upload.handler(buff -> {});
        upload.exceptionHandler(err -> events.add("upload-exception"));
        upload.endHandler(v -> events.add("upload-end"));
      });
      req.endHandler(v -> {
        events.add("end");
        req.response().end();
      });
    });
    startServer(testAddress);
    assertEquals(200, sendForm("multipart/form-data; boundary=" + boundary, Buffer.buffer(body), 0));
    assertWaitUntil(() -> events.contains("end"));
    assertEquals(List.of("upload-exception", "request-exception", "end"), events);
  }

  @Test
  public void testIllegalCharsetInFormContentType() throws Exception {
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    AtomicReference<Boolean> expectMultipart = new AtomicReference<>();
    server.requestHandler(req -> {
      try {
        req.setExpectMultipart(true);
      } catch (Throwable t) {
        thrown.set(t);
      }
      expectMultipart.set(req.isExpectMultipart());
      req.response().end();
    });
    startServer(testAddress);
    assertEquals(200, sendForm("multipart/form-data; boundary=XYZ; charset=@@@", Buffer.buffer("--XYZ--\r\n"), 0));
    assertWaitUntil(() -> expectMultipart.get() != null);
    assertTrue("Expected an IllegalStateException, got " + thrown.get(), thrown.get() instanceof IllegalStateException);
    assertFalse(expectMultipart.get());
  }

  @Test
  public void testFormDecodingErrorNotReportedByNettyAsDecoderError() throws Exception {
    // An illegal charset name in a part content type makes Netty throw an IllegalCharsetNameException
    // that is not wrapped in an HttpPostRequestDecoder exception
    String boundary = "a4e41223-a527-49b6-ac1c-315d76be757e";
    Buffer body = Buffer.buffer("--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"attr\"\r\n" +
      "Content-Type: text/plain; charset=@@@\r\n" +
      "\r\n" +
      "value\r\n" +
      "--" + boundary + "--\r\n");
    Buffer received = Buffer.buffer();
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(req -> {
      List<String> reqEvents = recordFormEvents(req);
      req.handler(received::appendBuffer);
      req.endHandler(v -> {
        events.addAll(reqEvents);
        events.add("end");
        req.response().setStatusCode(400).end();
      });
    });
    startServer(testAddress);
    assertEquals(400, sendForm("multipart/form-data; boundary=" + boundary, body, 0));
    assertWaitUntil(() -> events.contains("end"));
    assertEquals(1, Collections.frequency(events, "request-exception"));
    assertTrue(events.indexOf("request-exception") < events.indexOf("end"));
    assertEquals(body, received);
  }

  @Test
  public void testFormErrorGivesNoAttributes() throws Exception {
    String boundary = "a4e41223-a527-49b6-ac1c-315d76be757e";
    String body = "--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"attr\"\r\n" +
      "\r\n" +
      "value\r\n" +
      "--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
      "Content-Type: text/plain\r\n" +
      "\r\n" +
      "some-content-without-the-closing-boundary";
    AtomicReference<MultiMap> attributes = new AtomicReference<>();
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.uploadHandler(upload -> upload.handler(buff -> {}));
      req.exceptionHandler(err -> {});
      req.endHandler(v -> {
        attributes.set(MultiMap.caseInsensitiveMultiMap().addAll(req.formAttributes()));
        req.response().setStatusCode(400).end();
      });
    });
    startServer(testAddress);
    assertEquals(400, sendForm("multipart/form-data; boundary=" + boundary, Buffer.buffer(body), 0));
    assertTrue("Expected no attributes, got " + attributes.get(), attributes.get().isEmpty());
  }

  @Test
  public void testMultipartMediaTypeIsCaseInsensitive() throws Exception {
    String boundary = "a4e41223-a527-49b6-ac1c-315d76be757e";
    String body = "--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"name\"\r\n" +
      "\r\n" +
      "Julien\r\n" +
      "--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
      "Content-Type: text/plain\r\n" +
      "\r\n" +
      "hello\r\n" +
      "--" + boundary + "--\r\n";
    List<String> uploads = Collections.synchronizedList(new ArrayList<>());
    AtomicReference<MultiMap> attributes = new AtomicReference<>();
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.uploadHandler(upload -> {
        Buffer content = Buffer.buffer();
        upload.handler(content::appendBuffer);
        upload.endHandler(v -> uploads.add(upload.filename() + ":" + content));
      });
      req.endHandler(v -> {
        attributes.set(MultiMap.caseInsensitiveMultiMap().addAll(req.formAttributes()));
        req.response().end();
      });
    });
    startServer(testAddress);
    assertEquals(200, sendForm("Multipart/Form-Data; boundary=" + boundary, Buffer.buffer(body), 0));
    assertEquals("Expected the name attribute, got " + attributes.get(), "Julien", attributes.get().get("name"));
    assertEquals(1, attributes.get().size());
    assertWaitUntil(() -> !uploads.isEmpty());
    assertEquals(List.of("tmp-0.txt:hello"), uploads);
  }

  @Test
  public void testMultipartContentTypeWithoutBoundary() throws Exception {
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    AtomicReference<Boolean> expectMultipart = new AtomicReference<>();
    server.requestHandler(req -> {
      try {
        req.setExpectMultipart(true);
      } catch (Throwable t) {
        thrown.set(t);
      }
      expectMultipart.set(req.isExpectMultipart());
      req.response().end();
    });
    startServer(testAddress);
    assertEquals(200, sendForm("multipart/form-data; charset=UTF-8", Buffer.buffer("--XYZ--\r\n"), 0));
    assertWaitUntil(() -> expectMultipart.get() != null);
    assertTrue("Expected an IllegalStateException, got " + thrown.get(), thrown.get() instanceof IllegalStateException);
    assertFalse(expectMultipart.get());
  }
}
