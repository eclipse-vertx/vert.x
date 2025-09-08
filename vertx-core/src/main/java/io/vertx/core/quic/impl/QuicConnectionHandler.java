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
package io.vertx.core.quic.impl;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelOption;
import io.netty.handler.codec.quic.QuicConnectionCloseEvent;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.quic.ConnectionClose;
import io.vertx.core.quic.QuicConnection;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicConnectionHandler extends ChannelDuplexHandler implements NetworkMetrics<Object> {

  private final ContextInternal context;
  private final TransportMetrics<?> metrics;
  private Handler<QuicConnection> handler;
  private QuicChannel channel;
  private QuicConnectionImpl connection;

  public QuicConnectionHandler(ContextInternal context, TransportMetrics<?> metrics, Handler<QuicConnection> handler) {
    this.context = context;
    this.metrics = metrics;
    this.handler = handler;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    QuicChannel ch = (QuicChannel) ctx.channel();
    channel = ch;
    connection = new QuicConnectionImpl(context, metrics, ch, ctx);
    if (ch.isActive()) {
      activate();
    }
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    activate();
    super.channelActive(ctx);
  }

  private void activate() {
    if (metrics != null) {
      Object metric = metrics.connected(connection.remoteAddress(), connection.remoteName());
      connection.metric(metric);
    }
    Handler<QuicConnection> h = handler;
    if (h != null) {
      handler = null;
      context.dispatch(connection, h);
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof QuicStreamChannel) {
      QuicStreamChannel streamChannel = (QuicStreamChannel) msg;
//      streamChannel.config().setOption(QuicChannelOption.READ_FRAMES, true);
      connection.handleStream(streamChannel);
      super.channelRead(ctx, msg);
    } else {
      throw new UnsupportedOperationException("Handle " + msg);
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof QuicConnectionCloseEvent) {
      QuicConnectionCloseEvent closeEvent = (QuicConnectionCloseEvent) evt;
      handleClosed(closeEvent);
    }
    super.userEventTriggered(ctx, evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) {
    connection.handleException(t);
    chctx.close();
  }

  @Override
  public void channelInactive(ChannelHandlerContext chctx) {
    handleClosed(null);
  }

  void handleClosed(QuicConnectionCloseEvent event) {
    QuicConnectionImpl c = connection;
    if (c != null) {
      connection = null;
      ConnectionClose payload;
      if (event != null) {
        payload = new ConnectionClose();
        payload.setError(event.error());
          try {
              payload.setReason(Buffer.buffer(event.reason()));
          } catch (NullPointerException e) {
              // Todo: Netty minor bug
          }
      } else {
        payload = null;
      }
      c.handleClosed(payload);
    }
  }

  @Override
  public void bytesRead(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    NetworkMetrics.super.bytesRead(socketMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void bytesWritten(Object socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    NetworkMetrics.super.bytesWritten(socketMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void exceptionOccurred(Object socketMetric, SocketAddress remoteAddress, Throwable t) {
    NetworkMetrics.super.exceptionOccurred(socketMetric, remoteAddress, t);
  }
}
