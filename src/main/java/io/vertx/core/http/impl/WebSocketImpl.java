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

import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.vertx.core.http.WebSocket;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class WebSocketImpl extends WebSocketImplBase<WebSocketImpl> implements WebSocket {

  private long timerID = -1L;

  public WebSocketImpl(Http1xClientConnection conn,
                       boolean supportsContinuation,
                       int maxWebSocketFrameSize,
                       int maxWebSocketMessageSize) {
    super(conn, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize);
  }

  @Override
  void handleClosed() {
    synchronized (conn) {
      if (timerID != -1L) {
        conn.getContext().owner().cancelTimer(timerID);
      }
      HttpClientMetrics metrics = ((Http1xClientConnection) conn).metrics();
      if (metrics != null) {
        metrics.disconnected(getMetric());
      }
    }
    super.handleClosed();
  }

  @Override
  protected void doClose() {
    synchronized (conn) {
      timerID = conn.getContext().owner().setTimer(1000, id -> {
        synchronized (conn) {
          timerID = -1L;
        }
        conn.channelHandlerContext().close();
      });
    }
  }
}
