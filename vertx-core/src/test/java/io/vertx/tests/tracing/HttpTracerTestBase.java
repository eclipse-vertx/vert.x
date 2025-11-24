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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.test.http.HttpConfig;
import io.vertx.core.internal.ContextInternal;
import io.vertx.test.core.ContextLocalHelper;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.test.http.SimpleHttpTest;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public abstract class HttpTracerTestBase extends SimpleHttpTest {

  private VertxTracer tracer;
  private ContextLocal<Object> key;

  protected HttpTracerTestBase(HttpConfig config) {
    super(config);
  }

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
    server.requestHandler(req -> {
      assertEquals(1, seq.get());
      ContextInternal ctx = (ContextInternal) Vertx.currentContext();
      assertSame(val, key.get(ctx));
      req.response().closeHandler(v -> {
        assertNull(key.get(ctx));
        assertEquals(2, seq.get());
      });
      req.response().end();
    }).listen().await();
    client.request(HttpMethod.GET, "/").onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testHttpServerError() throws Exception {
    waitFor(2);
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
    server.requestHandler(req -> {
      assertEquals(1, seq.get());
      ContextInternal ctx = (ContextInternal) Vertx.currentContext();
      assertSame(val, key.get(ctx));
      req.exceptionHandler(v -> {
//        assertNull(ctx.localContextData().get(key));
//        assertEquals(2, seq.get());
        if (v instanceof HttpClosedException) {
          complete();
        }
      });
    }).listen().await();
    HttpClientRequest request = client.request(HttpMethod.GET, "/").await();
    request.response();
    request.setChunked(true)
      .writeHead()
      .await();
    assertWaitUntil(() -> seq.get() == 1);
    request.connection().close();
    try {
      request.response().await();
      fail();
    } catch (Exception expected) {
    }
    await();
  }

  @Test
  public void testHttpClientRequest() throws Exception {
    testHttpClientRequest(new RequestOptions(requestOptions).setURI("/"), "GET");
  }

  @Test
  public void testHttpClientRequestOverrideOperation() throws Exception {
    testHttpClientRequest(new RequestOptions(requestOptions).setURI("/").setTraceOperation("operation-override"), "operation-override");
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
        assertEquals(expectedOperation, operation);
        return request;
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        assertSame(val, key.get(context));
        key.remove(context);
        assertNotNull(response);
        assertNull(failure);
        assertTrue(seq.compareAndSet(1, 2));
      }
    });
    server.requestHandler(req -> {
      assertEquals(traceId, req.getHeader("X-B3-TraceId"));
      req.response().end();
    }).listen().await();
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
    server.requestHandler(req -> {
      assertEquals(traceId, req.getHeader("X-B3-TraceId"));
      req.connection().close();
    }).listen().await();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      key.put(ctx, val);
      client.request(HttpMethod.GET,"/").onComplete(onSuccess(req -> {
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
    this.server = createHttpServer();
    this.client = createHttpClient();
  }
}
