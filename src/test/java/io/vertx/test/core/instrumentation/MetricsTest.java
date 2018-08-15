/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */package io.vertx.test.core.instrumentation;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.*;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.instrumentation.Instrumentation;
import io.vertx.core.spi.instrumentation.InstrumentationFactory;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.core.instrumentation.tracer.Scope;
import io.vertx.test.core.instrumentation.tracer.Span;
import io.vertx.test.core.instrumentation.tracer.Tracer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class MetricsTest extends VertxTestBase {


  class Capture<T> implements Handler<T> {

    final Tracer tracer;
    final Span span;
    final Handler<T> handler;

    Capture(Tracer tracer, Handler<T> handler) {
      this.tracer = tracer;
      this.span = tracer.activeSpan();
      this.handler = handler;
    }

    @Override
    public void handle(T event) {
      Scope scope = tracer.activate(span);
      try {
        handler.handle(event);
      } finally {
        scope.close();
      }
    }
  }

  private Tracer tracer = new Tracer();

  @Override
  public void setUp() throws Exception {
    InstrumentationFactory.setInstrumentation(new Instrumentation() {
      @Override
      public <T> Handler<T> captureContinuation(Handler<T> handler) {
        if (tracer.activeSpan() != null) {
          return new Capture<>(tracer, handler);
        } else {
          return handler;
        }
      }

      @Override
      public <T> Handler<T> unwrapContinuation(Handler<T> wrapper) {
        return wrapper instanceof Capture ? ((Capture) wrapper).handler : null;
      }
    });
    super.setUp();
  }

  @Override
  protected VertxOptions getOptions() {
    return super.getOptions()
      .setMetricsOptions(new MetricsOptions().setFactory(options -> new DummyVertxMetrics() {
        @Override
        public HttpServerMetrics<Scope, Void, Void> createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
          return new HttpServerMetrics<Scope, Void, Void>() {
            @Override
            public Scope requestBegin(Void socketMetric, HttpServerRequest request) {
              // Just for test purposes
              // there should not be any active scope here
              // the previous processing should close all scopes
              if (tracer.activeSpan() != null) {
                throw new RuntimeException();
              }

              Span serverSpan = null;
              Span parent = tracer.decode(request.headers());
              if (parent != null) {
                serverSpan = tracer.createChild(parent);
              } else {
                serverSpan = tracer.newTrace();
              }
              serverSpan.addTag("span_kind", "server");
              serverSpan.addTag("path", request.path());
              serverSpan.addTag("query", request.query());
              // TODO here it feels a bit weird, we create a scope but we do not close it in the responseBegin! a possible leak
              // maybe pass a scope and close it in responseBegin
              return tracer.activate(serverSpan);
            }
            @Override
            public void afterRequestBegin(Scope scope) {
              scope.close();
            }
            @Override
            public void responseBegin(Scope scope, HttpServerResponse response) {
              scope.span().finish();
            }
          };
        }
        @Override
        public HttpClientMetrics<Span, Void, Void, Void, Void> createHttpClientMetrics(HttpClientOptions options) {
          return new HttpClientMetrics<Span, Void, Void, Void, Void>() {
            @Override
            public Span requestBegin(Void endpointMetric, Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
              // A bit hackish ?
              Handler handler = request.handler();
              Span span = handler instanceof Capture ?
                tracer.createChild(((Capture)handler).span) :
                tracer.newTrace();
              span.addTag("span_kind", "client");
              span.addTag("path", request.path());
              span.addTag("query", request.query());
              tracer.encode(span, request.headers());
              return span;
            }
            @Override
            public void responseBegin(Span requestMetric, HttpClientResponse response) {
              requestMetric.finish();
            }
          };
        }
      }).setEnabled(true));
  }

  @Test
  public void testHttpServerRequest() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    HttpClient client = vertx.createHttpClient();
    vertx.createHttpServer().requestHandler(req -> {
      assertNotNull(tracer.activeSpan());
      switch (req.path()) {
        case "/1": {
          vertx.setTimer(10, id -> {
            client.getNow(8080, "localhost", "/2", resp -> {
              req.response().end();
            });
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
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Span rootSpan = tracer.newTrace();
    tracer.activate(rootSpan);
    HttpClientRequest req = client.get(8080, "localhost", "/1", resp -> {
      assertEquals(rootSpan, tracer.activeSpan());
      assertEquals(200, resp.statusCode());
      List<Span> finishedSpans = tracer.getFinishedSpans();
      // client request to /1, server request /1, client request /2, server request /2
      assertEquals(4, finishedSpans.size());
      assertOneTrace(finishedSpans);
      testComplete();
    });
    req.end();
    await();
    assertEquals(rootSpan, tracer.activeSpan());
  }

  @Test
  public void testMultipleHttpServerRequest() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    HttpClient client = vertx.createHttpClient();
    vertx.createHttpServer().requestHandler(req -> {
      assertNotNull(tracer.activeSpan());
      switch (req.path()) {
        case "/1": {
          vertx.setTimer(10, id -> {
            client.getNow(8080, "localhost", "/2?" + req.query(), resp -> {
              req.response().end();
            });
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
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Span rootSpan = tracer.newTrace();
    tracer.activate(rootSpan);

    int numberOfRequests = 4;
    ExecutorService executorService = Executors.newFixedThreadPool(numberOfRequests);
    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < numberOfRequests; i++) {
      final int id = i;
      java.util.concurrent.Future<?> future = executorService.submit(() -> {
        tracer.activate(rootSpan);
        HttpClientRequest req = client.get(8080, "localhost", "/1?id=" + id, resp -> {
          assertEquals(rootSpan, tracer.activeSpan());
          assertEquals(200, resp.statusCode());
        });
        req.end();
      });
      futures.add(future);
    }

    for (java.util.concurrent.Future<?> future: futures) {
      future.get();
    }

    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.MINUTES);
    testComplete();
    await();
    waitUntil(() -> tracer.getFinishedSpans().size() == 4*numberOfRequests);
    assertEquals(rootSpan, tracer.activeSpan());
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertEquals(4*numberOfRequests, finishedSpans.size());
    assertOneTrace(finishedSpans);

    Map<Integer, Span> spanMap = finishedSpans.stream()
      .collect(Collectors.toMap(o -> o.id, Function.identity()));

    List<Span> lastServerSpans = finishedSpans.stream()
      .filter(mockSpan ->  mockSpan.getTags().get("span_kind").equals("server"))
      .filter(mockSpan -> mockSpan.getTags().get("path").contains("/2"))
      .collect(Collectors.toList());
    assertEquals(numberOfRequests, lastServerSpans.size());

    for (Span server2Span: lastServerSpans) {
      String queryStr = server2Span.getTags().get("query");
      Span client2Span = spanMap.get(server2Span.parentId);
      assertEquals("/2", client2Span.getTags().get("path"));
      assertEquals("client", client2Span.getTags().get("span_kind"));
      assertEquals(queryStr, client2Span.getTags().get("query"));
      Span server1Span = spanMap.get(client2Span.parentId);
      assertEquals("/1", server1Span.getTags().get("path"));
      assertEquals("server", server1Span.getTags().get("span_kind"));
      assertEquals(queryStr, server1Span.getTags().get("query"));
      Span client1Span = spanMap.get(server1Span.parentId);
      assertEquals("/1", client1Span.getTags().get("path"));
      assertEquals("client", client1Span.getTags().get("span_kind"));
      assertEquals(queryStr, client1Span.getTags().get("query"));
    }
  }

  void assertOneTrace(List<Span> spans) {
    for (int i = 1; i < spans.size(); i++) {
      assertEquals(spans.get(i - 1).traceId, spans.get(i).traceId);
    }
  }

}
