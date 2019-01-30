/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
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
import io.vertx.core.VertxOptions;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public abstract class HttpTracerTestBase extends HttpTestBase {

  private Tracer tracer;

  @Override
  protected Tracer getTracer() {
    return tracer = new Tracer() {
      @Override
      public Object receiveRequest(Map context, Object request, String operation, Iterable headers, Iterable tags) {
        return tracer.receiveRequest(context, request, operation, headers, tags);
      }
      @Override
      public void sendResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        tracer.sendResponse(context, response, payload, failure, tags);
      }
      @Override
      public Object sendRequest(Map context, Object request, String operation, BiConsumer headers, Iterable tags) {
        return tracer.sendRequest(context, request, operation, headers, tags);
      }
      @Override
      public void receiveResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        tracer.receiveResponse(context, response, payload, failure, tags);
      }
    };
  }

  @Test
  public void testHttpServer() throws Exception {
    Object key = new Object();
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new Tracer() {
      @Override
      public Object receiveRequest(Map context, Object request, String operation, Iterable headers, Iterable tags) {
        assertNull(context.put(key, val));
        assertTrue(seq.compareAndSet(0, 1));
        return request;
      }
      @Override
      public void sendResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        assertTrue(seq.compareAndSet(1, 2));
        assertNotNull(response);
        assertTrue(response instanceof HttpServerResponse);
        assertNull(failure);
        assertSame(val, context.remove(key));
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
    client.getNow(8080, "localhost", "/", onSuccess(resp -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testHttpServerError() throws Exception {
    waitFor(2);
    Object key = new Object();
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new Tracer() {
      @Override
      public Object receiveRequest(Map context, Object request, String operation, Iterable headers, Iterable tags) {
        assertNull(context.put(key, val));
        assertTrue(seq.compareAndSet(0, 1));
        return request;
      }
      @Override
      public void sendResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        assertTrue(seq.compareAndSet(1, 2));
        assertNull(response);
        assertNotNull(failure);
        assertNotNull(context.remove(key));
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
    HttpClientRequest req = client.get(8080, "localhost", "/", onFailure(err -> {
      complete();
    }));
    req.setChunked(true);
    req.sendHead(v -> {
      req.connection().close();
    });
    await();
  }

  @Test
  public void testHttpClientRequest() throws Exception {
    Object key = new Object();
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new Tracer() {
      @Override
      public Object sendRequest(Map context, Object outbound, String operation, BiConsumer headers, Iterable tags) {
        assertSame(val, context.get(key));
        assertTrue(seq.compareAndSet(0, 1));
        headers.accept("header-key","header-value");
        return outbound;
      }
      @Override
      public void receiveResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        assertSame(val, context.remove(key));
        assertNotNull(response);
        assertTrue(response instanceof HttpClientResponse);
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
      client.getNow(8080, "localhost", "/", onSuccess(resp -> {
        assertEquals(1, seq.get());
        resp.endHandler(v2 -> {
          vertx.runOnContext(v -> {
            assertEquals(2, seq.get());
            assertNull(tracerMap.get(key));
            testComplete();
          });
        });
      }));
    });
    await();
  }

  @Test
  public void testHttpClientError() throws Exception {
    Object key = new Object();
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    tracer = new Tracer() {
      @Override
      public Object sendRequest(Map context, Object request, String operation, BiConsumer headers, Iterable tags) {
        assertSame(val, context.get(key));
        assertTrue(seq.compareAndSet(0, 1));
        headers.accept("header-key","header-value");
        return request;
      }
      @Override
      public void receiveResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        assertSame(val, context.remove(key));
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
      client.getNow(8080, "localhost", "/", onFailure(err -> {
        assertEquals(2, seq.get());
        assertEquals(2, seq.get());
        assertNull(tracerMap.get(key));
        testComplete();
      }));
    });
    await();
  }
}
