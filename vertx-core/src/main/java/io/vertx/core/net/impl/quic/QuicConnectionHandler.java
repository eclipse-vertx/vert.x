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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicConnectionCloseEvent;
import io.netty.handler.codec.quic.QuicDatagramExtensionEvent;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamLimitChangedEvent;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.vertx.core.Completable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.SslHandshakeCompletionHandler;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ShutdownEvent;
import io.vertx.core.net.QuicConnectionClose;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.time.Duration;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicConnectionHandler extends ChannelDuplexHandler implements NetworkMetrics<Object> {

  private static long timeoutMillis(Duration timeout) {
    return timeout == null || timeout.isNegative() | timeout.isZero() ? -1L : timeout.toMillis();
  }

  private final ContextInternal context;
  private final TransportMetrics<?> metrics;
  private final long idleTimeout;
  private final long readIdleTimeout;
  private final long writeIdleTimeout;
  private final ByteBufFormat activityLogging;
  private final int maxStreamBidiRequests;
  private final int maxStreamUniRequests;
  private final boolean server;
  private final SocketAddress remoteAddress;
  private Completable<QuicConnection> handler;
  private QuicConnectionImpl connection;
  private int maxDatagramLength;

  public QuicConnectionHandler(ContextInternal context, TransportMetrics<?> metrics, Duration idleTimeout,
                               Duration readIdleTimeout, Duration writeIdleTimeout, ByteBufFormat activityLogging,
                               int maxStreamBidiRequests, int maxStreamUniRequests, SocketAddress remoteAddress,
                               boolean server, Completable<QuicConnection> handler) {
    this.context = context;
    this.metrics = metrics;
    this.idleTimeout = timeoutMillis(idleTimeout);
    this.readIdleTimeout = timeoutMillis(readIdleTimeout);
    this.writeIdleTimeout = timeoutMillis(writeIdleTimeout);
    this.activityLogging = activityLogging;
    this.maxStreamBidiRequests = maxStreamBidiRequests;
    this.maxStreamUniRequests = maxStreamUniRequests;
    this.handler = handler;
    this.remoteAddress = remoteAddress;
    this.server = server;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    activate(ctx);
  }

  private void activate(ChannelHandlerContext ctx) {
    QuicChannel ch = (QuicChannel) ctx.channel();
    QuicConnectionImpl c = new QuicConnectionImpl(context, metrics, idleTimeout, readIdleTimeout, writeIdleTimeout, activityLogging,
      maxStreamBidiRequests, maxStreamUniRequests, maxDatagramLength, ch, remoteAddress, ctx, server);
    connection = c;
    if (metrics != null) {
      Object metric = metrics.connected(c.remoteAddress(), c.remoteName());
      c.metric(metric);
    }
    Completable<QuicConnection> h = handler;
    if (h != null) {
      handler = null;
      h.succeed(c);
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof QuicStreamChannel) {
      QuicStreamChannel streamChannel = (QuicStreamChannel) msg;
//      streamChannel.config().setOption(QuicChannelOption.READ_FRAMES, true);
      connection.handleStream(streamChannel);
      super.channelRead(ctx, msg);
    } else if (msg instanceof ByteBuf)  {
      connection.handleDatagram((ByteBuf) msg);
    } else {
      throw new UnsupportedOperationException("Handle " + msg);
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof SniCompletionEvent) {
      SniCompletionEvent sniEvent = (SniCompletionEvent)evt;
      String serverName = sniEvent.hostname();
      if (serverName != null) {
        ctx.channel().attr(SslHandshakeCompletionHandler.SERVER_NAME_ATTR).set(serverName);
      }
    } else if (evt instanceof QuicConnectionCloseEvent) {
      QuicConnectionCloseEvent closeEvent = (QuicConnectionCloseEvent) evt;
      handleClosed(closeEvent);
    } else if (evt instanceof ShutdownEvent) {
      ShutdownEvent shutdown = (ShutdownEvent) evt;
      QuicConnectionImpl c = connection;
      if (c != null) {
        c.shutdown(shutdown.timeout());
      }
    } else if (evt instanceof QuicStreamLimitChangedEvent) {
      QuicConnectionImpl c = connection;
      if (c != null) {
        c.handleQuicStreamLimitChanged();
      }
    } else if (evt instanceof QuicDatagramExtensionEvent) {
      QuicDatagramExtensionEvent datagramExtensionEvent = (QuicDatagramExtensionEvent) evt;
      this.maxDatagramLength = datagramExtensionEvent.maxLength();
    } else if (evt instanceof SslHandshakeCompletionEvent) {
      SslHandshakeCompletionEvent handshakeEvt = (SslHandshakeCompletionEvent)evt;
      if (!handshakeEvt.isSuccess()) {
        Completable<QuicConnection> h = handler;
        if (h != null) {
          h.fail(handshakeEvt.cause());
        }
      }
    }
    super.userEventTriggered(ctx, evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) {
    QuicConnectionImpl c = connection;
    if (c != null && c.handleException(t)) {
      chctx.close();
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext chctx) {
    handleClosed(null);
  }

  void handleClosed(QuicConnectionCloseEvent event) {
    QuicConnectionImpl c = connection;
    if (c != null) {
      connection = null;
      QuicConnectionClose payload;
      if (event != null) {
        payload = new QuicConnectionClose();
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
  public void bytesRead(Object connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
    NetworkMetrics.super.bytesRead(connectionMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void bytesWritten(Object connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
    NetworkMetrics.super.bytesWritten(connectionMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void exceptionOccurred(Object connectionMetric, SocketAddress remoteAddress, Throwable err) {
    NetworkMetrics.super.exceptionOccurred(connectionMetric, remoteAddress, err);
  }
}
