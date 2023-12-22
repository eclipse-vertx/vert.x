/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.faketracer;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextKeyHelper;
import io.vertx.core.spi.context.ContextKey;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * @author Pavol Loffay
 */
public class FakeTracer implements VertxTracer<Span, Span> {

  private final ContextKey<Scope> scopeKey = ContextKey.registerKey(Scope.class);
  private AtomicInteger idGenerator = new AtomicInteger(0);
  List<Span> finishedSpans = new CopyOnWriteArrayList<>();
  private AtomicInteger closeCount = new AtomicInteger();

  int nextId() {
    return idGenerator.getAndIncrement();
  }

  private Span newTrace(SpanKind kind, String operation) {
    return new Span(this, kind, nextId(), nextId(), nextId(), operation);
  }

  public Span newTrace() {
    return new Span(this, null, nextId(), nextId(), nextId(), null);
  }

  public Span activeSpan() {
    return activeSpan(Vertx.currentContext());
  }

  public Span activeSpan(Context data) {
    Scope scope = data.getLocal(scopeKey);
    return scope != null ? scope.wrapped : null;
  }

  public Scope activate(Span span) {
    return activate(Vertx.currentContext(), span);
  }

  public Scope activate(Context context, Span span) {
    Scope toRestore = context.getLocal(scopeKey);
    Scope active = new Scope(this, span, toRestore);
    context.putLocal(scopeKey, active);
    return active;
  }

  public void encode(Span span, BiConsumer<String, String> headers) {
    headers.accept("span-trace-id", "" + span.traceId);
    headers.accept("span-parent-id", "" + span.parentId);
    headers.accept("span-id", "" + span.id);
  }

  private Span decode(SpanKind kind, String operation, Iterable<Map.Entry<String, String>> headers) {
    String traceId = null;
    String spanId = null;
    String spanParentId = null;
    for (Map.Entry<String, String> header : headers) {
      switch (header.getKey()) {
        case "span-trace-id":
          traceId = header.getValue();
          break;
        case "span-id":
          spanId = header.getValue();
          break;
        case "span-parent-id":
          spanParentId = header.getValue();
          break;
      }
    }
    if (traceId != null && spanId != null && spanParentId != null) {
        return new Span(this, kind, Integer.parseInt(traceId), Integer.parseInt(spanParentId),
          Integer.parseInt(spanId), operation);
    }
    return null;
  }

  private Span getServerSpan(SpanKind kind, TracingPolicy policy, String operation, Iterable<Map.Entry<String, String>> headers) {
    Span parent = decode(kind, operation, headers);
    if (parent != null) {
      return parent.createChild(kind, operation);
    } else if (policy == TracingPolicy.ALWAYS) {
      return newTrace(kind, operation);
    }
    return null;
  }

  @Override
  public <R> Span receiveRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
    if (policy == TracingPolicy.IGNORE) {
      return null;
    }
    Span serverSpan = getServerSpan(kind, policy, operation, headers);
    if (serverSpan == null) {
      return null;
    }
    serverSpan.addTag("span_kind", "server");
    addTags(serverSpan, request, tagExtractor);
    // Create scope
    return activate(context, serverSpan).span();
  }

  @Override
  public <R> void sendResponse(Context context, R response, Span span, Throwable failure, TagExtractor<R> tagExtractor) {
    if (span != null) {
      addTags(span, response, tagExtractor);
      span.finish(failure);
    }
  }

  @Override
  public <R> Span sendRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
    if (policy == TracingPolicy.IGNORE) {
      return null;
    }
    Span span = activeSpan(context);
    if (span != null) {
      span = span.createChild(kind, operation);
    } else if (policy == TracingPolicy.ALWAYS) {
      span = newTrace(kind, operation);
    } else {
      return null;
    }
    span.addTag("span_kind", "client");
    addTags(span, request, tagExtractor);
    encode(span, headers);
    return span;
  }

  @Override
  public <R> void receiveResponse(Context context, R response, Span span, Throwable failure, TagExtractor<R> tagExtractor) {
    if (span != null) {
      addTags(span, response, tagExtractor);
      span.finish(failure);
    }
  }

  private <T> void addTags(Span span, T obj, TagExtractor<T> tagExtractor) {
    if (obj != null) {
      int len = tagExtractor.len(obj);
      for (int idx = 0;idx < len;idx++) {
        span.addTag(tagExtractor.name(obj, idx), tagExtractor.value(obj, idx));
      }
    }
  }

  public List<Span> getFinishedSpans() {
    return Collections.unmodifiableList(finishedSpans);
  }

  @Override
  public void close() {
    closeCount.incrementAndGet();
    ContextKeyHelper.reset();
  }

  public int closeCount() {
    return closeCount.get();
  }
}
