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

package io.vertx.test.core.instrumentation.tracer;

import io.vertx.core.MultiMap;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Pavol Loffay
 */
public class Tracer {
  private AtomicInteger idGenerator = new AtomicInteger(0);
  List<Span> finishedSpans = new CopyOnWriteArrayList<>();
  ThreadLocal<Scope> currentSpan = new ThreadLocal<>();

  private int nextId() {
    return idGenerator.getAndIncrement();
  }

  public Span newTrace() {
    return new Span(this, nextId(), nextId(), nextId());
  }

  public Span createChild(Span span) {
    return new Span(this, span.traceId, span.id, nextId());
  }

  public Span activeSpan() {
    Scope scope = currentSpan.get();
    return scope != null ? scope.wrapped: null;
  }

  public Scope activate(Span span) {
    Scope toRestore = currentSpan.get();
    Scope active = new Scope(this, span, toRestore);
    currentSpan.set(active);
    span.currentScope = active;
    return active;
  }

  public void encode(Span span, MultiMap map) {
    map.set("span-trace-id", "" + span.traceId);
    map.set("span-parent-id", "" + span.parentId);
    map.set("span-id", "" + span.id);
  }

  public Span decode(MultiMap map) {
    String traceId = map.get("span-trace-id");
    String spanId = map.get("span-id");
    String spanParentId = map.get("span-parent-id");
    if (traceId != null && spanId != null && spanParentId != null) {
        return new Span(this, Integer.parseInt(traceId), Integer.parseInt(spanParentId),
          Integer.parseInt(spanId));
    }
    return null;
  }

  public List<Span> getFinishedSpans() {
    return Collections.unmodifiableList(finishedSpans);
  }

  public Span createSpan(int traceId, int parentId, int id) {
    return new Span(this, traceId, parentId, id);
  }
}
