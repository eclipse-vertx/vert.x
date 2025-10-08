/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.quic;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.quic.QuicException;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamFrame;
import io.netty.handler.codec.quic.QuicStreamType;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.impl.SocketBase;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicStream;
import io.vertx.core.spi.metrics.NetworkMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicStreamImpl extends SocketBase<QuicStreamImpl> implements QuicStreamInternal {

  private final QuicConnection connection;
  private final ContextInternal context;
  private final QuicStreamChannel channel;
  private final NetworkMetrics<?> streamMetrics;
  private final boolean bidirectional;
  private final boolean localCreated;
  private Handler<Integer> resetHandler;

  QuicStreamImpl(QuicConnection connection, ContextInternal context, QuicStreamChannel channel, NetworkMetrics<?> streamMetrics, ChannelHandlerContext chctx) {
    super(context, chctx);
    this.connection = connection;
    this.context = context;
    this.channel = channel;
    this.streamMetrics = streamMetrics;
    this.bidirectional = channel.type() == QuicStreamType.BIDIRECTIONAL;
    this.localCreated = channel.isLocalCreated();
  }

  @Override
  public QuicStream resetHandler(@Nullable Handler<Integer> handler) {
    this.resetHandler = handler;
    return this;
  }

  @Override
  public Future<Void> reset(int error) {
    PromiseInternal<Void> promise = context.promise();
    ChannelFuture shutdownPromise = channel.shutdownOutput(error);
    shutdownPromise.addListener(promise);
    return promise.future();
  }

  @Override
  public NetworkMetrics<?> metrics() {
    return streamMetrics;
  }

  @Override
  public Future<Void> writeMessage(Object message) {
    if (bidirectional || localCreated) {
      return super.writeMessage(message);
    } else {
      return context.failedFuture("Unidirectional stream created by the remote endpoint cannot be written to");
    }
  }

  @Override
  protected long sizeof(Object msg) {
    if (msg instanceof QuicStreamFrame) {
      return ((QuicStreamFrame)msg).content().readableBytes();
    } else {
      return super.sizeof(msg);
    }
  }

  @Override
  public Future<Void> end() {
    PromiseInternal<Void> promise = context.promise();
    ChannelFuture shutdownPromise = channel.shutdownOutput();
    shutdownPromise.addListener(promise);
    return promise.future();
  }

  @Override
  protected void handleEvent(Object event) {
    if (event == ChannelInputShutdownEvent.INSTANCE) {
      handleEnd();
    } else {
      super.handleEvent(event);
    }
  }

  @Override
  protected void handleException(Throwable t) {
    if (t instanceof QuicException) {
      QuicException quicException = (QuicException) t;
      if (quicException.error() == null && "STREAM_RESET".equals(quicException.getMessage())) {
        Handler<Integer> handler = resetHandler;
        if (handler != null) {
          context.emit(0, handler);
        }
        return;
      }
    }
    super.handleException(t);
  }

  @Override
  public boolean isLocalCreated() {
    return localCreated;
  }

  @Override
  public boolean isBidirectional() {
    return bidirectional;
  }

  @Override
  public QuicConnection connection() {
    return connection;
  }
}
