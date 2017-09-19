/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
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
