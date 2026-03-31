package io.vertx.tests.tracing;

import io.vertx.core.Context;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Map;
import java.util.function.BiConsumer;

public class TestTracer<I, O> implements VertxTracer<I, O> {

  public static <I, O> VertxTracer<I, O> wrap(VertxTracer<I, O> actual) {
    return new TestTracer<>(actual);
  }

  private final VertxTracer<I, O> actual;

  private TestTracer(VertxTracer<I, O> actual) {
    this.actual = actual;
  }

  @Override
  public <R> I receiveRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
    ContextInternal i = (ContextInternal) context;
    ContextInternal prev = i.beginDispatch();
    try {
      return actual.receiveRequest(context, kind, policy, request, operation, headers, tagExtractor);
    } finally {
      i.endDispatch(prev);
    }
  }

  @Override
  public <R> void sendResponse(Context context, R response, I payload, Throwable failure, TagExtractor<R> tagExtractor) {
    ContextInternal i = (ContextInternal) context;
    ContextInternal prev = i.beginDispatch();
    try {
      actual.sendResponse(context, response, payload, failure, tagExtractor);
    } finally {
      i.endDispatch(prev);
    }
  }

  @Override
  public <R> O sendRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
    ContextInternal i = (ContextInternal) context;
    ContextInternal prev = i.beginDispatch();
    try {
      return actual.sendRequest(context, kind, policy, request, operation, headers, tagExtractor);
    } finally {
      i.endDispatch(prev);
    }
  }

  @Override
  public <R> void receiveResponse(Context context, R response, O payload, Throwable failure, TagExtractor<R> tagExtractor) {
    ContextInternal i = (ContextInternal) context;
    ContextInternal prev = i.beginDispatch();
    try {
      actual.receiveResponse(context, response, payload, failure, tagExtractor);
    } finally {
      i.endDispatch(prev);
    }
  }

  @Override
  public void close() {
    actual.close();
  }
}
