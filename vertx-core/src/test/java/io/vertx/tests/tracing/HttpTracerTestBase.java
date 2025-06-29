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
package io.vertx.tests.tracing;

import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.test.http.HttpTestBase;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.test.core.ContextLocalHelper;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.tracing.TracingPolicy;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public abstract class HttpTracerTestBase extends HttpTestBase {

  private VertxTracer tracer;
  private ContextLocal<Object> key;

  @Override
  public void setUp() throws Exception {
    key = ContextLocal.registerLocal(Object.class);
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    ContextLocalHelper.reset();
    super.tearDown();
  }

  @Override
  protected VertxTracer getTracer() {
    return tracer;
  }

  @Test
  public void testHttpServer() throws Exception {
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    setTracer(new VertxTracer() {
      @Override
      public Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, Iterable headers, TagExtractor tagExtractor) {
        assertNull(key.get(context));
        key.put(context, val);
        assertTrue(seq.compareAndSet(0, 1));
        return request;
      }
      @Override
      public void sendResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertTrue(seq.compareAndSet(1, 2));
        assertNotNull(response);
        assertTrue(response instanceof HttpResponse);
        assertNull(failure);
        assertSame(val, key.get(context));
        key.remove(context);
      }
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals(1, seq.get());
      ContextInternal ctx = (ContextInternal) Vertx.currentContext();
      assertSame(val, key.get(ctx));
      req.response().closeHandler(v -> {
        assertNull(key.get(ctx));
        assertEquals(2, seq.get());
      });
      req.response().end();
    }).listen(DEFAULT_HTTP_PORT, "localhost").onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, "localhost", "/").onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testHttpServerError() throws Exception {
    waitFor(3);
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    setTracer(new VertxTracer() {
      @Override
      public Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, Iterable headers, TagExtractor tagExtractor) {
        assertNull(key.get(context));
        key.put(context, val);
        assertTrue(seq.compareAndSet(0, 1));
        return request;
      }
      @Override
      public void sendResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertTrue(seq.compareAndSet(1, 2));
        assertNull(response);
        assertNotNull(failure);
        complete();
      }
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals(1, seq.get());
      ContextInternal ctx = (ContextInternal) Vertx.currentContext();
      assertSame(val, key.get(ctx));
      req.exceptionHandler(v -> {
//        assertNull(ctx.localContextData().get(key));
//        assertEquals(2, seq.get());
        complete();
      });
    }).listen(DEFAULT_HTTP_PORT, "localhost").onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost("localhost")
      .setURI("/")).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onFailure(err -> {
          complete();
        }));
      req.setChunked(true)
        .writeHead().onComplete(v -> {
          req.connection().close();
        });
    }));
    await();
  }

  @Test
  public void testHttpClientRequest() throws Exception {
    testHttpClientRequest(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setHost("localhost").setURI("/"), "GET");
  }

  @Test
  public void testHttpClientRequestOverrideOperation() throws Exception {
    testHttpClientRequest(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setHost("localhost").setURI("/").setTraceOperation("operation-override"), "operation-override");
  }

  private void testHttpClientRequest(RequestOptions request, String expectedOperation) throws Exception {
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    String traceId = UUID.randomUUID().toString();
    setTracer(new VertxTracer() {
      @Override
      public Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, BiConsumer headers, TagExtractor tagExtractor) {
        assertSame(val, key.get(context));
        assertTrue(seq.compareAndSet(0, 1));
        headers.accept("X-B3-TraceId", traceId);
        assertNotNull(request);
        assertTrue(request instanceof HttpRequest);
        assertEquals(expectedOperation, operation);
        return request;
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertSame(val, key.get(context));
        key.remove(context);
        assertNotNull(response);
        assertTrue(response instanceof HttpResponse);
        assertNull(failure);
        assertTrue(seq.compareAndSet(1, 2));
      }
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals(traceId, req.getHeader("X-B3-TraceId"));
      req.response().end();
    }).listen(DEFAULT_HTTP_PORT, "localhost").onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = vertx.getOrCreateContext();
    Promise<Void> response = Promise.promise();
    ctx.runOnContext(v1 -> {
      key.put(ctx, val);
      client.request(request).compose(req -> req.send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::end))
        .onComplete(response);
    });
    response.future().await(20, TimeUnit.SECONDS);
    // Updates are done on the HTTP client context, so we need to run task on this context
    // to avoid data race
    assertWaitUntil(() -> key.get(ctx) == null);
  }

  @Test
  public void testHttpClientError() throws Exception {
    waitFor(2);
    Object val = new Object();
    AtomicInteger seq = new AtomicInteger();
    String traceId = UUID.randomUUID().toString();
    setTracer(new VertxTracer() {
      @Override
      public Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, BiConsumer headers, TagExtractor tagExtractor) {
        assertSame(val, key.get(context));
        assertTrue(seq.compareAndSet(0, 1));
        headers.accept("X-B3-TraceId", traceId);
        return request;
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertSame(val, key.get(context));
        key.remove(context);
        assertNull(response);
        assertNotNull(failure);
        assertTrue(seq.compareAndSet(1, 2));
        complete();
      }
    });
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
      assertEquals(traceId, req.getHeader("X-B3-TraceId"));
      req.connection().close();
    }).listen(DEFAULT_HTTP_PORT, "localhost").onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      key.put(ctx, val);
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, "localhost", "/").onComplete(onSuccess(req -> {
        req.send().onComplete(onFailure(err -> {
          // assertNull(tracerMap.get(key));
          complete();
        }));
      }));
    });
    await();
  }

  protected void setTracer(VertxTracer tracer) {
    this.server.close();
    this.tracer = tracer;
    // So, the vertx options is reset with the new tracer.
    this.vertx = vertx(getOptions());
    this.server = this.vertx.createHttpServer(createBaseServerOptions());
    this.client = this.vertx.createHttpClient(createBaseClientOptions());
  }
}
