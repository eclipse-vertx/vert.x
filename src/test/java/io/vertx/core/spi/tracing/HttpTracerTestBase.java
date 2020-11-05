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
package io.vertx.core.spi.tracing;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public abstract class HttpTracerTestBase extends HttpTestBase {

  private VertxTracer tracer;

  @Override
  protected VertxTracer getTracer() {
    return tracer = new VertxTracer() {
      @Override
      public Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, Iterable headers, TagExtractor tagExtractor) {
        return tracer.receiveRequest(context, kind, policy, request, operation, headers, tagExtractor);
      }
      @Override
      public void sendResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        tracer.sendResponse(context, response, payload, failure, tagExtractor);
      }
      @Override
      public Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, BiConsumer headers, TagExtractor tagExtractor) {
        return tracer.sendRequest(context, kind, policy, request, operation, headers, tagExtractor);
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        tracer.receiveResponse(context, response, payload, failure, tagExtractor);
      }
    };
  }

  @Test
  public void testHttpServer() throws Exception {
    String key = TestUtils.randomAlphaString(10);
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new VertxTracer() {
      @Override
      public Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, Iterable headers, TagExtractor tagExtractor) {
        assertNull(context.getLocal(key));
        context.putLocal(key, val);
        assertTrue(seq.compareAndSet(0, 1));
        return request;
      }
      @Override
      public void sendResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertTrue(seq.compareAndSet(1, 2));
        assertNotNull(response);
        assertTrue(response instanceof HttpServerResponse);
        assertNull(failure);
        assertSame(val, context.getLocal(key));
        assertTrue(context.removeLocal(key));
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals(1, seq.get());
      ContextInternal ctx = (ContextInternal) Vertx.currentContext();
      assertSame(val, ctx.localContextData().get(key));
      req.response().endHandler(v -> {
        assertNull(ctx.localContextData().get(key));
        assertEquals(2, seq.get());
      });
      req.response().end();
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.request(HttpMethod.GET, 8080, "localhost", "/", onSuccess(req -> {
      req.send(onSuccess(resp -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testHttpServerError() throws Exception {
    waitFor(2);
    String key = TestUtils.randomAlphaString(10);
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new VertxTracer() {
      @Override
      public Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, Iterable headers, TagExtractor tagExtractor) {
        assertNull(context.getLocal(key));
        context.putLocal(key, val);
        assertTrue(seq.compareAndSet(0, 1));
        return request;
      }
      @Override
      public void sendResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertTrue(seq.compareAndSet(1, 2));
        assertNull(response);
        assertNotNull(failure);
        assertTrue(context.removeLocal(key));
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals(1, seq.get());
      ContextInternal ctx = (ContextInternal) Vertx.currentContext();
      assertSame(val, ctx.localContextData().get(key));
      req.exceptionHandler(v -> {
        assertNull(ctx.localContextData().get(key));
        assertEquals(2, seq.get());
        complete();
      });
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.request(new RequestOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/")).onComplete(onSuccess(req -> {
      req
        .response(onFailure(err -> {
          complete();
        }))
        .setChunked(true)
        .sendHead(v -> {
          req.connection().close();
        });
    }));
    await();
  }

  @Test
  public void testHttpClientRequest() throws Exception {
    String key = TestUtils.randomAlphaString(10);
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new VertxTracer() {
      @Override
      public Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, BiConsumer headers, TagExtractor tagExtractor) {
        assertSame(val, context.getLocal(key));
        assertTrue(seq.compareAndSet(0, 1));
        headers.accept("header-key","header-value");
        assertNotNull(request);
        assertTrue(request instanceof HttpRequest);
        return request;
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertSame(val, context.getLocal(key));
        assertTrue(context.removeLocal(key));
        assertNotNull(response);
        assertTrue(response instanceof HttpResponse);
        assertNull(failure);
        assertTrue(seq.compareAndSet(1, 2));
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals("header-value", req.getHeader("header-key"));
      req.response().end();
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(key, val);
      client.request(HttpMethod.GET, 8080, "localhost", "/", onSuccess(req -> {
        req.send(onSuccess(resp -> {
          resp.endHandler(v2 -> {
            // Updates are done on the HTTP client context, so we need to run task on this context
            // to avoid data race
            ctx.runOnContext(v -> {
              assertNull(tracerMap.get(key));
              testComplete();
            });
          });
        }));
      }));
    });
    await();
  }

  @Test
  public void testHttpClientError() throws Exception {
    String key = TestUtils.randomAlphaString(10);
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new VertxTracer() {
      @Override
      public Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, BiConsumer headers, TagExtractor tagExtractor) {
        assertSame(val, context.getLocal(key));
        assertTrue(seq.compareAndSet(0, 1));
        headers.accept("header-key","header-value");
        return request;
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertSame(val, context.getLocal(key));
        assertTrue(context.removeLocal(key));
        assertNull(response);
        assertNotNull(failure);
        assertTrue(seq.compareAndSet(1, 2));
      }
    };
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals("header-value", req.getHeader("header-key"));
      req.connection().close();
    }).listen(8080, "localhost", onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(key, val);
      client.request(HttpMethod.GET, 8080, "localhost", "/", onSuccess(req -> {
        req.send(onFailure(err -> {
          assertEquals(2, seq.get());
          assertEquals(2, seq.get());
          assertNull(tracerMap.get(key));
          testComplete();
        }));
      }));
    });
    await();
  }
}
