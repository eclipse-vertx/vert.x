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
import io.vertx.core.impl.ContextImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Pool extends ConnectionManager.Pool {

  private Http2ClientConnection clientHandler;
  final HttpClientImpl client;

  public Http2Pool(ConnectionManager.ConnQueue queue, HttpClientImpl client) {
    super(queue, 1);
    this.client = client;
  }

  // Under sync when called
  public boolean getConnection(Waiter waiter) {
    Http2ClientConnection conn = this.clientHandler;
    if (conn != null && canReserveStream(conn)) {
      conn.streamCount++;
      ContextImpl context = waiter.context;
      if (context == null) {
        context = conn.getContext();
      } else if (context != conn.getContext()) {
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
    synchronized (queue) {
      VertxHttp2ConnectionHandler handler = new VertxHttp2ConnectionHandlerBuilder()
          .server(false)
          .useCompression(client.getOptions().isTryUseCompression())
          .initialSettings(client.getOptions().getHttp2Settings())
          .connectionFactory(connHandler -> new Http2ClientConnection(Http2Pool.this, context, ch, connHandler))
          .build();
      Http2ClientConnection conn = (Http2ClientConnection) handler.connection;
      clientHandler = conn;
      p.addLast(handler);
      conn.streamCount++;
      waiter.handleConnection(conn); // Should make same tests than in deliverRequest
      deliverStream(conn, waiter);
      checkPending(conn);
    }
  }

  private boolean canReserveStream(Http2ClientConnection handler) {
    int maxConcurrentStreams = handler.connHandler.connection().local().maxActiveStreams();
    return handler.streamCount < maxConcurrentStreams;
  }

  void checkPending(Http2ClientConnection handler) {
    synchronized (queue) {
      Waiter waiter;
      while (canReserveStream(handler) && (waiter = queue.getNextWaiter()) != null) {
        handler.streamCount++;
        deliverStream(handler, waiter);
      }
    }
  }

  void discard(Http2ClientConnection conn) {
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
      Http2ClientConnection handler = (Http2ClientConnection) conn;
      handler.streamCount--;
      checkPending(handler);
    }
  }

  @Override
  HttpClientStream createStream(HttpClientConnection conn) {
    return ((Http2ClientConnection)conn).createStream();
  }

  @Override
  void closeAllConnections() {
    // todo
  }
}
