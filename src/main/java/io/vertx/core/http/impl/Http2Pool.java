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
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2Exception;
import io.vertx.core.impl.ContextImpl;

import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Pool extends ConnectionManager.Pool {

  private Http2ClientConnection connection;
  private final Map<Channel, Http2ClientConnection> connectionMap;
  final HttpClientImpl client;

  public Http2Pool(ConnectionManager.ConnQueue queue, HttpClientImpl client, Map<Channel, Http2ClientConnection> connectionMap) {
    super(queue, 1);
    this.client = client;
    this.connectionMap = connectionMap;
  }

  // Under sync when called
  public boolean getConnection(Waiter waiter) {
    Http2ClientConnection conn = this.connection;
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

  void createConn(ContextImpl context, Channel ch, Waiter waiter, boolean upgrade) {
    ChannelPipeline p = ch.pipeline();
    synchronized (queue) {
      VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>()
          .connectionMap(connectionMap)
          .server(false)
          .useCompression(client.getOptions().isTryUseCompression())
          .initialSettings(client.getOptions().getInitialSettings())
          .connectionFactory(connHandler -> new Http2ClientConnection(Http2Pool.this, context, ch, connHandler))
          .build();
      if (upgrade) {
        try {
          handler.onHttpClientUpgrade();
        } catch (Http2Exception e) {
          e.printStackTrace();
        }
      }
      Http2ClientConnection conn = handler.connection;
      connection = conn;
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
      if (connection == conn) {
        connection = null;
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
    Http2ClientConnection conn;
    synchronized (queue) {
      conn = this.connection;
    }
    // Close outside sync block to avoid deadlock
    if (conn != null) {
      conn.close();
    }
  }
}
