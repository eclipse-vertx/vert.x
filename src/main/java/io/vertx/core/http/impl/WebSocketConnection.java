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
import io.netty.handler.codec.http.websocketx.*;
import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketFrameType;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.ShutdownEvent;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.util.function.Function;

import static io.vertx.core.net.impl.VertxHandler.safeBuffer;
import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * WebSocket connection.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
final class WebSocketConnection extends ConnectionBase {

  private WebSocketImplBase<?> webSocket;
  final TCPMetrics metrics;

  WebSocketConnection(ContextInternal context, ChannelHandlerContext chctx, TCPMetrics metrics) {
    super(context, chctx);
    this.metrics = metrics;
  }

  WebSocketImplBase<?> webSocket() {
    return webSocket;
  }

  WebSocketConnection webSocket(WebSocketImplBase<?> webSocket) {
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
  public Future<Void> close() {
    webSocket.close();
    return closeFuture();
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

  protected void handleEvent(Object evt) {
    if (evt instanceof ShutdownEvent) {
      ShutdownEvent shutdown = (ShutdownEvent) evt;
      webSocket.close();
    } else {
      super.handleEvent(evt);
    }
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
    if (w != null) {
      w.context().execute(frame, w::handleFrame);
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
