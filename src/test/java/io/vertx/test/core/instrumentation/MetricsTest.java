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

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
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
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MetricsTest extends VertxTestBase {

  static class Span {

    final int traceId;
    final int parentId;
    final int id;
    private int nextChildId = 0;

    private Span(int traceId, int parentId, int id) {
      this.traceId = traceId;
      this.parentId = parentId;
      this.id = id;
    }

    synchronized Span createChild() {
      return new Span(traceId, id, nextChildId++);
    }

    void encode(MultiMap map) {
      map.set("span-trace-id", "" + traceId);
      map.set("span-parent-id", "" + parentId);
      map.set("span-id", "" + id);
    }

    @Override
    public boolean equals(Object obj) {
      Span span = (Span) obj;
      return span.traceId == traceId && span.parentId == parentId && span.id == id;
    }

    @Override
    public String toString() {
      return "Span[traceId=" + traceId + ",parentId=" + parentId + ",id=" + id + "]";
    }

    static Span decode(MultiMap map) {
      String traceId = map.get("span-trace-id");
      String spanId = map.get("span-id");
      if (traceId != null && spanId != null) {
        String spanParentId = map.get("span-parent-id");
        if (spanParentId != null) {
          return new Span(Integer.parseInt(traceId), Integer.parseInt(spanParentId), Integer.parseInt(spanId));
        } else {
          return new Span(Integer.parseInt(traceId), 0, Integer.parseInt(spanId));
        }
      }
      return null;
    }
  }

  private List<Span> log = Collections.synchronizedList(new ArrayList<>());

  private ThreadLocal<Span> currentSpan = new ThreadLocal<>();

  class Capture<T> implements Handler<T> {

    final Span span;
    final Handler<T> handler;


    Capture(Span span, Handler<T> handler) {
      this.span = span;
      this.handler = handler;
    }

    @Override
    public void handle(T event) {
      currentSpan.set(span);
      try {
        handler.handle(event);
      } finally {
        currentSpan.set(null);
      }
    }
  }

  @Override
  public void setUp() throws Exception {
    InstrumentationFactory.setInstrumentation(new Instrumentation() {
      @Override
      public <T> Handler<T> captureContinuation(Handler<T> handler) {
        Span current = currentSpan.get();
        if (current != null) {
          return new Capture<>(current, handler);
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
        public HttpServerMetrics<Span, Void, Void> createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
          return new HttpServerMetrics<Span, Void, Void>() {
            @Override
            public Span requestBegin(Void socketMetric, HttpServerRequest request) {
              Span span = Span.decode(request.headers());
              if (span != null) {
                currentSpan.set(span);
              } else {
                currentSpan.set(null);
              }
              return span;
            }
            @Override
            public void responseBegin(Span span, HttpServerResponse response) {
              if (span != null) {
                log.add(span);
              }
            }
          };
        }
        @Override
        public HttpClientMetrics<Span, Void, Void, Void, Void> createHttpClientMetrics(HttpClientOptions options) {
          return new HttpClientMetrics<Span, Void, Void, Void, Void>() {
            @Override
            public Span requestBegin(Void endpointMetric, Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
              // A bit hackish ?
              Span span = null;
              Handler<HttpClientResponse> handler = request.handler();
              if (handler instanceof Capture) {
                span = ((Capture)handler).span.createChild();
              }
              if (span != null) {
                span.encode(request.headers());
              }
              return span;
            }
            @Override
            public void responseBegin(Span requestMetric, HttpClientResponse response) {
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
      switch (req.path()) {
        case "/1": {
          vertx.setTimer(10, id -> {
            client.getNow(8080, "localhost", "/2", resp -> {
              req.response().end();
            });
          });
          // Mess up with counter
          currentSpan.set(null);
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
    HttpClientRequest req = client.get(8080, "localhost", "/1", resp -> {
      assertEquals(200, resp.statusCode());
      assertEquals(2, log.size());
      assertEquals(new Span(0, 0, 0), log.get(0));
      assertEquals(new Span(0, -1, 0), log.get(1));

      testComplete();
    });
    new Span(0, -1, 0).encode(req.headers());
    req.end();
    await();
  }


}
