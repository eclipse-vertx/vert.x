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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.impl.ConnectionBase;

import static io.vertx.core.net.impl.VertxHandler.safeBuffer;

public abstract class Http3ConnectionBase extends ConnectionBase implements HttpConnection {

  protected VertxHttp3ConnectionHandler<? extends Http3ConnectionBase> handler;

  public Http3ConnectionBase(EventLoopContext context,
                             VertxHttp3ConnectionHandler<? extends Http3ConnectionBase> connHandler) {
    super(context, connHandler.context());
    this.handler = connHandler;
  }

  @Override
  public void handleClosed() {
    super.handleClosed();
  }

  @Override
  protected void handleInterestedOpsChanged() {

  }

  //  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, VertxHttpStreamBase<?, ?, Http3Headers> stream,
                            Http3Headers headers, boolean endOfStream) throws Http2Exception {
    onHeadersRead(stream, headers, null, endOfStream);
  }

  //  @Override
  public int onDataRead(ChannelHandlerContext ctx, VertxHttpStreamBase<?, ?, Http3Headers> stream, ByteBuf data,
                        int padding, boolean endOfStream) {
    if (stream != null) {
      data = safeBuffer(data);
      Buffer buff = Buffer.buffer(data);
      stream.onData(buff);
      if (endOfStream) {
        stream.onEnd();
      }
    }
    return padding;
  }

  @Override
  public int getWindowSize() {
    return -1;
  }

  @Override
  public HttpConnection setWindowSize(int windowSize) {
    return HttpConnection.super.setWindowSize(windowSize);
  }

  @Override
  public HttpConnection goAway(long errorCode) {
    return HttpConnection.super.goAway(errorCode);
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId) {
    return HttpConnection.super.goAway(errorCode, lastStreamId);
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    return null;
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    return null;
  }

  @Override
  public void shutdown(Handler<AsyncResult<Void>> handler) {
    HttpConnection.super.shutdown(handler);
  }

  @Override
  public Future<Void> shutdown() {
    return HttpConnection.super.shutdown();
  }

  @Override
  public synchronized HttpConnection shutdownHandler(Handler<Void> handler) {
//    shutdownHandler = handler;
    return this;
  }

  @Override
  public void shutdown(long timeout, Handler<AsyncResult<Void>> handler) {
    shutdown(timeout, vertx.promise(handler));
  }

  @Override
  public Future<Void> shutdown(long timeoutMs) {
    PromiseInternal<Void> promise = vertx.promise();
    shutdown(timeoutMs, promise);
    return promise.future();
  }

  private void shutdown(long timeout, PromiseInternal<Void> promise) {
//    if (timeout < 0) {
//      promise.fail("Invalid timeout value " + timeout);
//      return;
//    }
//    handler.gracefulShutdownTimeoutMillis(timeout);
//    ChannelFuture fut = channel().close();
//    fut.addListener(promise);
  }

  @Override
  public Http3ConnectionBase closeHandler(Handler<Void> handler) {
    return (Http3ConnectionBase) super.closeHandler(handler);
  }

  @Override
  public Http2Settings settings() {
    return null;
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    return null;
  }

  @Override
  public HttpConnection updateSettings(Http2Settings settings, Handler<AsyncResult<Void>> completionHandler) {
    return null;
  }

  @Override
  public Http2Settings remoteSettings() {
    return null;
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    return null;
  }

  @Override
  public HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
    return null;
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    return null;
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    return null;
  }

  @Override
  public Http3ConnectionBase exceptionHandler(Handler<Throwable> handler) {
    return (Http3ConnectionBase) super.exceptionHandler(handler);
  }

  public VertxInternal vertx() {
    return this.vertx;
  }

  public void consumeCredits(QuicStreamChannel stream, int len) {
//    throw new RuntimeException("Method not implemented");
  }

  protected abstract void onHeadersRead(VertxHttpStreamBase<?, ?, Http3Headers> stream, Http3Headers headers,
                                        StreamPriorityBase streamPriority, boolean endOfStream);

}
