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

package io.vertx.test.faketracer;

import io.vertx.core.spi.tracing.SpanKind;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Pavol Loffay
 */
public class Span {

  public final SpanKind kind;
  public final int traceId;
  public final int parentId;
  public final int id;
  public final String operation;
  final FakeTracer tracer;
  private AtomicBoolean finished = new AtomicBoolean();
  private final Map<String, String> tags = new ConcurrentHashMap<>();

  Span(FakeTracer tracer, SpanKind kind, int traceId, int parentId, int id, String operation) {
    this.kind = kind;
    this.tracer = tracer;
    this.traceId = traceId;
    this.parentId = parentId;
    this.id = id;
    this.operation = operation;
  }

  public Map<String, String> getTags() {
    return Collections.unmodifiableMap(tags);
  }

  public Span createChild(SpanKind kind, String operation) {
    return new Span(tracer, kind, traceId, id, tracer.nextId(), operation);
  }

  public void addTag(String key, String value) {
    if (value != null) {
      tags.put(key, value);
    }
  }

  public void finish() {
    if (finished.getAndSet(true)) {
      throw new IllegalStateException("Finishing already finished span!");
    }
    tracer.finishedSpans.add(this);
  }

  @Override
  public boolean equals(Object obj) {
    Span span = (Span) obj;
    return span.kind == kind && span.traceId == traceId && span.parentId == parentId && span.id == id;
  }

  @Override
  public String toString() {
    return "Span[kind=" + kind.name() + ",traceId=" + traceId + ",parentId=" + parentId + ",id=" + id + "]";
  }
}
