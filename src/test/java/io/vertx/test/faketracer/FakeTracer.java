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

package io.vertx.test.faketracer;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.spi.tracing.VertxTracer;

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

  private static final String ACTIVE_SCOPE_KEY = "active.scope";

  private AtomicInteger idGenerator = new AtomicInteger(0);
  List<Span> finishedSpans = new CopyOnWriteArrayList<>();
  private AtomicInteger closeCount = new AtomicInteger();

  int nextId() {
    return idGenerator.getAndIncrement();
  }

  public Span newTrace(String operation) {
    return new Span(this, nextId(), nextId(), nextId(), operation);
  }

  public Span newTrace() {
    return new Span(this, nextId(), nextId(), nextId(), null);
  }

  public Span activeSpan() {
    return activeSpan(Vertx.currentContext());
  }

  public Span activeSpan(Context data) {
    Scope scope = data.getLocal(ACTIVE_SCOPE_KEY);
    return scope != null ? scope.wrapped : null;
  }

  public Scope activate(Span span) {
    return activate(Vertx.currentContext(), span);
  }

  public Scope activate(Context context, Span span) {
    Scope toRestore = context.getLocal(ACTIVE_SCOPE_KEY);
    Scope active = new Scope(this, span, toRestore);
    context.putLocal(ACTIVE_SCOPE_KEY, active);
    return active;
  }

  public void encode(Span span, BiConsumer<String, String> headers) {
    headers.accept("span-trace-id", "" + span.traceId);
    headers.accept("span-parent-id", "" + span.parentId);
    headers.accept("span-id", "" + span.id);
  }

  public Span decode(String operation, Iterable<Map.Entry<String, String>> headers) {
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
        return new Span(this, Integer.parseInt(traceId), Integer.parseInt(spanParentId),
          Integer.parseInt(spanId), operation);
    }
    return null;
  }

  private Span getServerSpan(String operation, Iterable<Map.Entry<String, String>> headers) {
    Span parent = decode(operation, headers);
    if (parent != null) {
      return parent.createChild(operation);
    } else {
      return newTrace(operation);
    }
  }

  @Override
  public Span receiveRequest(Context context, Object inbound, String operation, Iterable<Map.Entry<String, String>> headers, Iterable<Map.Entry<String, String>> tags) {
    Span serverSpan = getServerSpan(operation, headers);
    serverSpan.addTag("span_kind", "server");
    addTags(serverSpan, tags);
    // Create scope
    return activate(context, serverSpan).span();
  }

  @Override
  public void sendResponse(Context context, Object response, Span span, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
    if (span != null) {
      addTags(span, tags);
      span.finish();
    }
  }

  @Override
  public Span sendRequest(Context context, Object outbound, String operation, BiConsumer<String, String> headers, Iterable<Map.Entry<String, String>> tags) {
    Span span = activeSpan(context);
    if (span == null) {
      span = newTrace(operation);
    } else {
      span = span.createChild(operation);
    }
    span.addTag("span_kind", "client");
    addTags(span, tags);
    encode(span, headers);
    return span;
  }

  @Override
  public void receiveResponse(Context context, Object response, Span span, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
    if (span != null) {
      addTags(span, tags);
      span.finish();
    }
  }

  private void addTags(Span span, Iterable<Map.Entry<String, String>> tags) {
    for (Map.Entry<String, String> tag : tags) {
      span.addTag(tag.getKey(), tag.getValue());
    }
  }

  public List<Span> getFinishedSpans() {
    return Collections.unmodifiableList(finishedSpans);
  }

  @Override
  public void close() {
    closeCount.incrementAndGet();
  }

  public int closeCount() {
    return closeCount.get();
  }
}
