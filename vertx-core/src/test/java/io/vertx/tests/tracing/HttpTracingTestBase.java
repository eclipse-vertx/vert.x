/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.tracing;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;
import io.vertx.test.core.ProvidedBy;
import io.vertx.test.core.VertxProvider;
import io.vertx.test.faketracer.FakeTracer;
import io.vertx.test.faketracer.Span;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.SimpleHttpTest;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class HttpTracingTestBase extends SimpleHttpTest2 {

  public class TracingVertxProvider implements VertxProvider {

    @Override
    public Vertx call() throws Exception {
      FakeTracer tracer = new FakeTracer();
      HttpTracingTestBase.this.tracer = tracer;
      return Vertx.builder()
        .withTracer(options -> tracer)
        .build();
    }
  }

  private FakeTracer tracer;

  protected HttpTracingTestBase(HttpConfig config) {
    super(config);
  }

  @Before
  @Override
  public void before(@ProvidedBy(TracingVertxProvider.class) Vertx vertx) throws Exception {
    super.before(vertx);
  }

  @Test
  public void testHttpServerRequest() throws Exception {
    server.requestHandler(req -> {
      assertNotNull(tracer.activeSpan());
      req.response().end();
    });
    startServer();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Span rootSpan = tracer.newTrace();
      tracer.activate(rootSpan);
      client.request(HttpMethod.GET,"/1")
        .compose(HttpClientRequest::send).onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(rootSpan, tracer.activeSpan());
          assertEquals(200, resp.statusCode());
        }));
    });
    TestUtils.waitUntil(() -> tracer.getFinishedSpans().size() == 2);
    assertSingleTrace(tracer.getFinishedSpans());
  }

  @Test
  public void testHttpServerRequestWithClient() throws Exception {
    server.requestHandler(req -> {
      assertNotNull(tracer.activeSpan());
      switch (req.path()) {
        case "/1": {
          vertx.setTimer(10, id1 -> {
            client
              .request(HttpMethod.GET,"/2")
              .compose(HttpClientRequest::send)
              .onComplete(TestUtils.onSuccess(resp1 -> {
                vertx.setTimer(10, id2 -> {
                  client
                    .request(HttpMethod.GET,"/2")
                    .compose(HttpClientRequest::send)
                    .onComplete(TestUtils.onSuccess(resp2 -> req.response().end()));
                });
              }));
          });
          break;
        }
        case "/2": {
          req.response().end();
          break;
        }
        default: {
          req.response().setStatusCode(500).end();
        }
      }
    });
    startServer();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Span rootSpan = tracer.newTrace();
      tracer.activate(rootSpan);
      client
        .request(HttpMethod.GET,"/1")
        .compose(req -> req
          .send()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::end))
        .onComplete(TestUtils.onSuccess(v2 -> {
          assertEquals(rootSpan, tracer.activeSpan());
        }));
    });
    // client request to /1, server request /1, client request /2, server request /2
    TestUtils.waitUntil(() -> tracer.getFinishedSpans().size() == 6);
    assertSingleTrace(tracer.getFinishedSpans());
  }

  @Test
  public void testMultipleHttpServerRequest() throws Exception {


    AtomicBoolean ssl = new AtomicBoolean();
    server.requestHandler(serverReq -> {
      ssl.set(serverReq.isSSL());
      assertNotNull(tracer.activeSpan());
      switch (serverReq.path()) {
        case "/1": {
          vertx.setTimer(10, id -> {
            client.request(HttpMethod.GET,"/2?q=true")
              .compose(HttpClientRequest::send)
              .onComplete(TestUtils.onSuccess(resp -> {
                serverReq.response().end();
              }));
          });
          break;
        }
        case "/2": {
          serverReq.response().end();
          break;
        }
        default: {
          serverReq.response().setStatusCode(500).end();
        }
      }
    });
    startServer();

    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Span rootSpan = tracer.newTrace();
      tracer.activate(rootSpan);
      client.request(HttpMethod.GET,"/1")
        .compose(HttpClientRequest::send)
        .onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(rootSpan, tracer.activeSpan());
          assertEquals(200, resp.statusCode());
        }));
    });


    TestUtils.waitUntil(() -> tracer.getFinishedSpans().size() == 4);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertEquals(4, finishedSpans.size());
    assertSingleTrace(finishedSpans);

    Map<Integer, Span> spanMap = finishedSpans.stream()
      .collect(Collectors.toMap(o -> o.id, Function.identity()));

    List<Span> lastServerSpans = finishedSpans.stream()
      .filter(mockSpan ->  mockSpan.getTags().get("span_kind").equals("server"))
      .filter(mockSpan -> mockSpan.getTags().get("http.url").contains(config.host() + ":" + config.port() + "/2"))
      .collect(Collectors.toList());
    assertEquals(1, lastServerSpans.size());

    String scheme = ssl.get() ? "https" : "http";
    for (Span server2Span: lastServerSpans) {
      assertEquals(scheme, server2Span.getTags().get("url.scheme"));
      assertEquals("/2", server2Span.getTags().get("url.path"));
      assertEquals("q=true", server2Span.getTags().get("url.query"));
      Span client2Span = spanMap.get(server2Span.parentId);
      assertEquals("GET", client2Span.operation);
      assertEquals(scheme + "://" + config.host() + ":" + config.port() + "/2?q=true", client2Span.getTags().get("url.full"));
      assertEquals("200", client2Span.getTags().get("http.response.status_code"));
      assertEquals("client", client2Span.getTags().get("span_kind"));
      Span server1Span = spanMap.get(client2Span.parentId);
      assertEquals("GET", server1Span.operation);
      assertEquals("200", client2Span.getTags().get("http.response.status_code"));
      assertEquals("server", server1Span.getTags().get("span_kind"));
      Span client1Span = spanMap.get(server1Span.parentId);
      assertEquals("GET", client1Span.operation);
      assertEquals(scheme + "://" + config.host() + ":" + config.port() + "/1", client1Span.getTags().get("url.full"));
      assertEquals("200", client2Span.getTags().get("http.response.status_code"));
      assertEquals("client", client1Span.getTags().get("span_kind"));
    }
  }

  private void assertSingleTrace(List<Span> spans) {
    for (int i = 1; i < spans.size(); i++) {
      assertEquals(spans.get(i - 1).traceId, spans.get(i).traceId);
    }
  }
}
