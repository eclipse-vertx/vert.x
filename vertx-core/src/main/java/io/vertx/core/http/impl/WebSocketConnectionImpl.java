/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
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
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.http.WebSocketFrameType;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.impl.VertxConnection;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.util.concurrent.TimeUnit;

import static io.vertx.core.net.impl.VertxHandler.safeBuffer;
import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * WebSocket connection.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
final class WebSocketConnectionImpl extends VertxConnection {

  private final long closingTimeoutMS;
  private ScheduledFuture<?> closingTimeout;
  private final boolean server;
  private final TCPMetrics metrics;
  private WebSocketImplBase<?> webSocket;
  private boolean closeSent;
  private ChannelPromise closePromise;
  private Object closeReason;
  private boolean closeReceived;

  WebSocketConnectionImpl(ContextInternal context, ChannelHandlerContext chctx, boolean server, long closingTimeoutMS, TCPMetrics metrics) {
    super(context, chctx);
    this.closingTimeoutMS = closingTimeoutMS;
    this.metrics = metrics;
    this.server = server;
  }

  WebSocketImplBase<?> webSocket() {
    return webSocket;
  }

  WebSocketConnectionImpl webSocket(WebSocketImplBase<?> webSocket) {
    this.webSocket = webSocket;
    return this;
  }

  @Override
  protected long sizeof(Object obj) {
    if (obj instanceof WebSocketFrame) {
      return ((WebSocketFrame) obj).content().readableBytes();
    }
    return super.sizeof(obj);
  }

  @Override
  public NetworkMetrics metrics() {
    return metrics;
  }

  @Override
  protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
    //
  }

  @Override
  protected void writeClose(Object reason, ChannelPromise promise) {
    assert !closeSent;
    closeSent = true;
    closePromise = promise;
    closeReason = reason;
    CloseWebSocketFrame closeFrame;
    if (reason instanceof CloseWebSocketFrame) {
      closeFrame = (CloseWebSocketFrame) reason;
    } else {
      closeFrame = closeFrame((short)1000, null);
    }
    if (closeReceived) {
      ChannelPromise channelPromise = chctx.newPromise();
      writeToChannel(closeFrame, channelPromise);
      if (server) {
        channelPromise.addListener(future -> finishClose());
      }
    } else {
      ChannelPromise channelPromise = chctx.newPromise();
      writeToChannel(closeFrame, channelPromise);
      if (closingTimeoutMS > 0L) {
        channelPromise.addListener(future -> {
          EventExecutor exec = chctx.executor();
          closingTimeout = exec.schedule(() -> {
            closingTimeout = null;
            finishClose();
          }, closingTimeoutMS, TimeUnit.MILLISECONDS);
        });
      } else if (closingTimeoutMS == 0L) {
        channelPromise.addListener(future -> finishClose());
      }
    }
  }

  private CloseWebSocketFrame closeFrame(short statusCode, String reason) {
    ByteBuf byteBuf = HttpUtils.generateWSCloseFrameByteBuf(statusCode, reason);
    return new CloseWebSocketFrame(true, 0, byteBuf);
  }

  @Override
  public void handleException(Throwable t) {
    WebSocketImplBase<?> ws = webSocket;
    if (ws != null) {
      ws.context().execute(t, ws::handleException);
    }
  }

  @Override
  protected void handleWriteQueueDrained() {
    WebSocketImplBase<?> ws = webSocket;
    if (ws != null) {
      ws.context().execute(ws::handleWriteQueueDrained);
    }
  }

  @Override
  protected void handleClosed() {
    ScheduledFuture<?> timeout = closingTimeout;
    if (timeout != null) {
      timeout.cancel(false);
    }
    if (closePromise != null) {
      closePromise.setSuccess();
    }
    Object metric = null;
    WebSocketImplBase<?> ws = webSocket;
    if (ws != null) {
      ws.context().execute(v -> ws.handleConnectionClosed());
      metric = ws.getMetric();
      ws.setMetric(null);
    }
    // Improve this with a common super interface to both
    if (this.metrics instanceof HttpServerMetrics) {
      HttpServerMetrics metrics = (HttpServerMetrics) this.metrics;
      if (METRICS_ENABLED && metrics != null) {
        metrics.disconnected(metric);
      }
    } else if (this.metrics instanceof HttpClientMetrics) {
      HttpClientMetrics metrics = (HttpClientMetrics) this.metrics;
      if (METRICS_ENABLED && metrics != null) {
        metrics.disconnected(metric);
      }
    }
    super.handleClosed();
  }

  @Override
  protected void handleMessage(Object msg) {
    if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;
      handleWsFrame(frame);
    }
  }

  void handleWsFrame(WebSocketFrame msg) {
    WebSocketFrameInternal frame = decodeFrame(msg);
    WebSocketImplBase<?> w;
    synchronized (this) {
      w = webSocket;
    }
    if (frame.isClose()) {
      closeReceived = true;
      if (!closeSent) {
        close(closeFrame(frame.closeStatusCode(), frame.closeReason())); // Reason
      } else {
        if (server) {
          finishClose();
        }
      }
    }
    if (w != null) {
      w.handleFrame(frame);
    }
  }

  private void finishClose() {
    // Do we really need to test timeout ????
    ScheduledFuture<?> timeout = closingTimeout;
    if (timeout == null || timeout.cancel(false)) {
      closingTimeout = null;
      ChannelPromise p = closePromise;
      closePromise = null;
      super.writeClose(closeReason, p);
    }
  }

  private WebSocketFrameInternal decodeFrame(io.netty.handler.codec.http.websocketx.WebSocketFrame msg) {
    ByteBuf payload = safeBuffer(msg.content());
    boolean isFinal = msg.isFinalFragment();
    WebSocketFrameType frameType;
    if (msg instanceof BinaryWebSocketFrame) {
      frameType = WebSocketFrameType.BINARY;
    } else if (msg instanceof CloseWebSocketFrame) {
      frameType = WebSocketFrameType.CLOSE;
    } else if (msg instanceof PingWebSocketFrame) {
      frameType = WebSocketFrameType.PING;
    } else if (msg instanceof PongWebSocketFrame) {
      frameType = WebSocketFrameType.PONG;
    } else if (msg instanceof TextWebSocketFrame) {
      frameType = WebSocketFrameType.TEXT;
    } else if (msg instanceof ContinuationWebSocketFrame) {
      frameType = WebSocketFrameType.CONTINUATION;
    } else {
      throw new IllegalStateException("Unsupported WebSocket msg " + msg);
    }
    return new WebSocketFrameImpl(frameType, payload, isFinal);
  }
}
