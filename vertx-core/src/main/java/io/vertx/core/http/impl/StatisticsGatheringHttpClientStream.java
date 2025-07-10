/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.http2.Http2ClientPush;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.endpoint.ServerInteraction;

/**
 * Decorates an {@link HttpClientStream} that gathers usage statistics.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class StatisticsGatheringHttpClientStream implements HttpClientStream {

  private final HttpClientStream delegate;
  private final ServerInteraction endpointRequest;

  StatisticsGatheringHttpClientStream(HttpClientStream delegate, ServerInteraction endpointRequest) {
    this.delegate = delegate;
    this.endpointRequest = endpointRequest;
  }

  @Override
  public int id() {
    return delegate.id();
  }

  @Override
  public Object metric() {
    return delegate.metric();
  }

  @Override
  public Object trace() {
    return delegate.trace();
  }

  @Override
  public HttpVersion version() {
    return delegate.version();
  }

  @Override
  public HttpClientConnection connection() {
    return delegate.connection();
  }

  @Override
  public ContextInternal context() {
    return delegate.context();
  }

  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect) {
    endpointRequest.reportRequestBegin();
    if (end) {
      endpointRequest.reportRequestEnd();
    }
    return delegate.writeHead(request, chunked, buf, end, priority, connect);
  }

  @Override
  public Future<Void> write(ByteBuf buf, boolean end) {
    if (end) {
      endpointRequest.reportRequestEnd();
    }
    return delegate.write(buf, end);
  }

  @Override
  public Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
    return delegate.writeFrame(type, flags, payload);
  }

  @Override
  public HttpClientStream continueHandler(Handler<Void> handler) {
    delegate.continueHandler(handler);
    return this;
  }

  @Override
  public HttpClientStream earlyHintsHandler(Handler<MultiMap> handler) {
    delegate.earlyHintsHandler(handler);
    return this;
  }

  @Override
  public HttpClientStream pushHandler(Handler<Http2ClientPush> handler) {
    delegate.pushHandler(handler);
    return this;
  }

  @Override
  public HttpClientStream unknownFrameHandler(Handler<HttpFrame> handler) {
    delegate.unknownFrameHandler(handler);
    return this;
  }

  @Override
  public HttpClientStream headHandler(Handler<HttpResponseHead> handler) {
    if (handler != null) {
      delegate.headHandler(multimap -> {
        endpointRequest.reportResponseBegin();
        handler.handle(multimap);
      });
    } else {
      delegate.headHandler(null);
    }
    return this;
  }

  @Override
  public HttpClientStream handler(Handler<Buffer> handler) {
    delegate.handler(handler);
    return this;
  }

  @Override
  public HttpClientStream trailersHandler(Handler<MultiMap> handler) {
    delegate.trailersHandler(handler);
    return this;
  }

  @Override
  public HttpClientStream endHandler(Handler<Void> handler) {
    if (handler != null) {
      delegate.endHandler(multimap -> {
        endpointRequest.reportResponseEnd();
        handler.handle(multimap);
      });
    } else {
      delegate.endHandler(null);
    }
    return this;
  }

  @Override
  public HttpClientStream priorityHandler(Handler<StreamPriority> handler) {
    delegate.priorityHandler(handler);
    return this;
  }

  @Override
  public HttpClientStream closeHandler(Handler<Void> handler) {
    delegate.closeHandler(handler);
    return this;
  }

  @Override
  public HttpClientStream pause() {
    delegate.pause();
    return this;
  }

  @Override
  public HttpClientStream fetch(long amount) {
    delegate.fetch(amount);
    return this;
  }

  @Override
  public HttpClientStream resume() {
    delegate.resume();
    return this;
  }

  @Override
  public Future<Void> reset(Throwable cause) {
    return delegate.reset(cause);
  }

  @Override
  public StreamPriority priority() {
    return delegate.priority();
  }

  @Override
  public HttpClientStream updatePriority(StreamPriority streamPriority) {
    delegate.updatePriority(streamPriority);
    return this;
  }

  @Override
  public HttpClientStream exceptionHandler(@Nullable Handler<Throwable> handler) {
    if (handler != null) {
      delegate.exceptionHandler(err -> {
        endpointRequest.reportFailure(err);
        handler.handle(err);
      });
    } else {
      delegate.exceptionHandler(null);
    }
    return this;
  }

  @Override
  @Fluent
  public HttpClientStream setWriteQueueMaxSize(int maxSize) {
    return delegate.setWriteQueueMaxSize(maxSize);
  }

  @Override
  public boolean writeQueueFull() {
    return delegate.writeQueueFull();
  }

  @Override
  @Fluent
  public HttpClientStream drainHandler(@Nullable Handler<Void> handler) {
    return delegate.drainHandler(handler);
  }
}
