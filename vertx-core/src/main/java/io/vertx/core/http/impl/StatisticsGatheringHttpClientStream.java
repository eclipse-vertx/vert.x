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
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.endpoint.ServerInteraction;
import io.vertx.core.streams.WriteStream;

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
  public HttpClientConnectionInternal connection() {
    return delegate.connection();
  }

  @Override
  public ContextInternal getContext() {
    return delegate.getContext();
  }

  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriorityBase priority, boolean connect) {
    endpointRequest.reportRequestBegin();
    if (end) {
      endpointRequest.reportRequestEnd();
    }
    return delegate.writeHead(request, chunked, buf, end, priority, connect);
  }

  @Override
  public Future<Void> writeBuffer(ByteBuf buf, boolean end) {
    if (end) {
      endpointRequest.reportRequestEnd();
    }
    return delegate.writeBuffer(buf, end);
  }

  @Override
  public Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
    return delegate.writeFrame(type, flags, payload);
  }

  @Override
  public void continueHandler(Handler<Void> handler) {
    delegate.continueHandler(handler);
  }

  @Override
  public void earlyHintsHandler(Handler<MultiMap> handler) {
    delegate.earlyHintsHandler(handler);
  }

  @Override
  public void pushHandler(Handler<HttpClientPush> handler) {
    delegate.pushHandler(handler);
  }

  @Override
  public void unknownFrameHandler(Handler<HttpFrame> handler) {
    delegate.unknownFrameHandler(handler);
  }

  @Override
  public void headHandler(Handler<HttpResponseHead> handler) {
    if (handler != null) {
      delegate.headHandler(multimap -> {
        endpointRequest.reportResponseBegin();
        handler.handle(multimap);
      });
    } else {
      delegate.headHandler(null);
    }
  }

  @Override
  public void chunkHandler(Handler<Buffer> handler) {
    delegate.chunkHandler(handler);
  }

  @Override
  public void endHandler(Handler<MultiMap> handler) {
    if (handler != null) {
      delegate.endHandler(multimap -> {
        endpointRequest.reportResponseEnd();
        handler.handle(multimap);
      });
    } else {
      delegate.endHandler(null);
    }
  }

  @Override
  public void priorityHandler(Handler<StreamPriorityBase> handler) {
    delegate.priorityHandler(handler);
  }

  @Override
  public void closeHandler(Handler<Void> handler) {
    delegate.closeHandler(handler);
  }

  @Override
  public void doSetWriteQueueMaxSize(int size) {
    delegate.doSetWriteQueueMaxSize(size);
  }

  @Override
  public boolean isNotWritable() {
    return delegate.isNotWritable();
  }

  @Override
  public void doPause() {
    delegate.doPause();
  }

  @Override
  public void doFetch(long amount) {
    delegate.doFetch(amount);
  }

  @Override
  public Future<Void> reset(Throwable cause) {
    return delegate.reset(cause);
  }

  @Override
  public StreamPriorityBase priority() {
    return delegate.priority();
  }

  @Override
  public void updatePriority(StreamPriorityBase streamPriority) {
    delegate.updatePriority(streamPriority);
  }

  @Override
  public WriteStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
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
  public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
    return delegate.setWriteQueueMaxSize(maxSize);
  }

  @Override
  public boolean writeQueueFull() {
    return delegate.writeQueueFull();
  }

  @Override
  @Fluent
  public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
    return delegate.drainHandler(handler);
  }

  @Override
  public StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_STREAM_PRIORITY;
  }
}
