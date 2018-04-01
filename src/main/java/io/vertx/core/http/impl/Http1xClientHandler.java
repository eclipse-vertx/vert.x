/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xClientHandler extends VertxHttpHandler<Http1xClientConnection> {
  private boolean closeFrameSent;
  private ContextInternal context;
  private ChannelHandlerContext chctx;
  private final HttpVersion version;
  private final String peerHost;
  private final String host;
  private final int port;
  private final boolean ssl;
  private final HttpClientImpl client;
  private final HttpClientMetrics metrics;
  private final ConnectionListener<HttpClientConnection> listener;
  private final Object endpointMetric;

  public Http1xClientHandler(ConnectionListener<HttpClientConnection> listener,
                             ContextInternal context,
                             HttpVersion version,
                             String peerHost,
                             String host,
                             int port,
                             boolean ssl,
                             HttpClientImpl client,
                             Object endpointMetric,
                             HttpClientMetrics metrics) {
    this.context = context;
    this.version = version;
    this.client = client;
    this.peerHost = peerHost;
    this.host = host;
    this.port = port;
    this.ssl = ssl;
    this.endpointMetric = endpointMetric;
    this.metrics = metrics;
    this.listener = listener;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    chctx = ctx;
    Http1xClientConnection conn = new Http1xClientConnection(listener, version, client, endpointMetric, ctx, ssl, peerHost, host, port, context, metrics);
    if (metrics != null) {
      context.executeFromIO(v -> {
        Object metric = metrics.connected(conn.remoteAddress(), conn.remoteName());
        conn.metric(metric);
        metrics.endpointConnected(endpointMetric, metric);
      });
    }
    setConnection(conn);
  }

  public ChannelHandlerContext context() {
    return chctx;
  }

  @Override
  public void channelInactive(ChannelHandlerContext chctx) throws Exception {
    if (metrics != null) {
      metrics.endpointDisconnected(endpointMetric, getConnection().metric());
    }
    super.channelInactive(chctx);
  }

  @Override
  protected void handleMessage(Http1xClientConnection conn, Object msg) {
    if (msg instanceof HttpObject) {
      HttpObject obj = (HttpObject) msg;
      DecoderResult result = obj.decoderResult();
      if (result.isFailure()) {
        // Close the connection as Netty's HttpResponseDecoder will not try further processing
        // see https://github.com/netty/netty/issues/3362
        conn.handleException(result.cause());
        conn.close();
        return;
      }
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) obj;
        conn.handleResponse(response);
        return;
      }
      if (msg instanceof HttpContent) {
        HttpContent chunk = (HttpContent) obj;
        if (chunk.content().isReadable()) {
          Buffer buff = Buffer.buffer(chunk.content().slice());
          conn.handleResponseChunk(buff);
        }
        if (chunk instanceof LastHttpContent) {
          conn.handleResponseEnd((LastHttpContent) chunk);
        }
        return;
      }
    } else if (msg instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
      switch (frame.type()) {
        case BINARY:
        case CONTINUATION:
        case TEXT:
        case PONG:
          conn.handleWsFrame(frame);
          break;
        case PING:
          // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
          chctx.writeAndFlush(new PongWebSocketFrame(frame.getBinaryData().copy()));
          break;
        case CLOSE:
          conn.handleWsFrame(frame);
          if (!closeFrameSent) {
            // Echo back close frame and close the connection once it was written.
            // This is specified in the WebSockets RFC 6455 Section  5.4.1
            chctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
            closeFrameSent = true;
          }
          break;
        default:
          throw new IllegalStateException("Invalid type: " + frame.type());
      }
      return;
    }
    throw new IllegalStateException("Invalid object " + msg);
  }
}
