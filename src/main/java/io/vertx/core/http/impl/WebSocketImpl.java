/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketFrame;
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
public class WebSocketImpl extends WebSocketImplBase<WebSocket> implements WebSocket {

  public WebSocketImpl(VertxInternal vertx,
                       ClientConnection conn, boolean supportsContinuation,
                       int maxWebSocketFrameSize, int maxWebSocketMessageSize) {
    super(vertx, conn, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize);
  }

  @Override
  void handleClosed() {
    synchronized (conn) {
      HttpClientMetrics metrics = ((ClientConnection) conn).metrics();
      if (metrics != null) {
        metrics.disconnected(getMetric());
      }
      super.handleClosed();
    }
  }
}
