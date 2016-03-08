/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Settings;
import io.vertx.core.impl.ContextImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Pool extends ConnectionManager.Pool {

  private VertxHttp2ClientHandler clientHandler;
  final HttpClientImpl client;

  public Http2Pool(ConnectionManager.ConnQueue queue, HttpClientImpl client) {
    super(queue, 1);
    this.client = client;
  }

  // Under sync when called
  public boolean getConnection(Waiter waiter) {
    VertxHttp2ClientHandler conn = this.clientHandler;
    if (conn != null && canReserveStream(conn)) {
      conn.streamCount++;
      ContextImpl context = waiter.context;
      if (context == null) {
        context = conn.context;
      } else if (context != conn.context) {
        ConnectionManager.log.warn("Reusing a connection with a different context: an HttpClient is probably shared between different Verticles");
      }
      context.runOnContext(v -> {
        deliverStream(conn, waiter);
      });
      return true;
    } else {
      return false;
    }
  }

  void createConn(ChannelHandlerContext handlerCtx, ContextImpl context, Channel ch, Waiter waiter) {
    ChannelPipeline p = ch.pipeline();
    Http2Connection connection = new DefaultHttp2Connection(false);
    VertxClientHandlerBuilder clientHandlerBuilder = new VertxClientHandlerBuilder(handlerCtx, context, ch);
    synchronized (queue) {
      VertxHttp2ClientHandler handler = clientHandlerBuilder.build(connection);
      handler.decoder().frameListener(handler);
      clientHandler = handler;
      p.addLast(handler);
      handler.streamCount++;
      waiter.handleConnection(handler); // Should make same tests than in deliverRequest
      deliverStream(handler, waiter);
      checkPending(handler);
    }
  }

  private boolean canReserveStream(VertxHttp2ClientHandler handler) {
    int maxConcurrentStreams = handler.connection().local().maxActiveStreams();
    return handler.streamCount < maxConcurrentStreams;
  }

  void checkPending(VertxHttp2ClientHandler handler) {
    synchronized (queue) {
      Waiter waiter;
      while (canReserveStream(handler) && (waiter = queue.getNextWaiter()) != null) {
        handler.streamCount++;
        deliverStream(handler, waiter);
      }
    }
  }

  void discard(VertxHttp2ClientHandler conn) {
    synchronized (queue) {
      if (clientHandler == conn) {
        clientHandler = null;
        queue.connectionClosed();
      }
    }
  }

  @Override
  void recycle(HttpClientConnection conn) {
    synchronized (queue) {
      VertxHttp2ClientHandler handler = (VertxHttp2ClientHandler) conn;
      handler.streamCount--;
      checkPending(handler);
    }
  }

  @Override
  HttpClientStream createStream(HttpClientConnection conn) {
    return ((VertxHttp2ClientHandler)conn).createStream();
  }

  @Override
  void closeAllConnections() {
    // todo
  }

  class VertxClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<VertxHttp2ClientHandler, VertxClientHandlerBuilder> {

    private final ChannelHandlerContext handlerCtx;
    private final ContextImpl context;
    private final Channel channel;

    public VertxClientHandlerBuilder(ChannelHandlerContext handlerCtx, ContextImpl context, Channel channel) {
      this.handlerCtx = handlerCtx;
      this.context = context;
      this.channel = channel;
    }

    @Override
    protected VertxHttp2ClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
      VertxHttp2ClientHandler handler = new VertxHttp2ClientHandler(Http2Pool.this, handlerCtx, context, channel, decoder, encoder, initialSettings);
      frameListener(handler);
      return handler;
    }

    public VertxHttp2ClientHandler build(Http2Connection conn) {
      connection(conn);
      io.vertx.core.http.Http2Settings initialSettings = client.getOptions().getHttp2Settings();
      if (initialSettings != null) {
        if (initialSettings.getHeaderTableSize() != null) {
          initialSettings().headerTableSize(initialSettings.getHeaderTableSize());
        }
        if (initialSettings.getInitialWindowSize() != null) {
          initialSettings().initialWindowSize(initialSettings.getInitialWindowSize());
        }
        if (initialSettings.getMaxConcurrentStreams() != null) {
          initialSettings().maxConcurrentStreams(initialSettings.getMaxConcurrentStreams());
        }
        if (initialSettings.getMaxFrameSize() != null) {
          initialSettings().maxFrameSize(initialSettings.getMaxFrameSize());
        }
        if (initialSettings.getMaxHeaderListSize() != null) {
          initialSettings().maxHeaderListSize(initialSettings.getMaxHeaderListSize());
        }
        if (initialSettings.getEnablePush() != null) {
          initialSettings().pushEnabled(initialSettings.getEnablePush());
        }
      }
      return super.build();
    }
  }
}
