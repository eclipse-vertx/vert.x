/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.http.WebSocket;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

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

  private final Http1xClientConnection conn;
  private final long closingTimeoutMS;

  public WebSocketImpl(ContextInternal context,
                       Http1xClientConnection conn,
                       boolean supportsContinuation,
                       long closingTimeout,
                       int maxWebSocketFrameSize,
                       int maxWebSocketMessageSize) {
    super(context, conn, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize);
    this.conn = conn;
    this.closingTimeoutMS = closingTimeout >= 0 ? closingTimeout * 1000L : -1L;
  }

  @Override
  protected void handleCloseConnection() {
    if (closingTimeoutMS == 0L) {
      closeConnection();
    } else if (closingTimeoutMS > 0L) {
      initiateConnectionCloseTimeout(closingTimeoutMS);
    }
  }

  @Override
  protected void handleClose(boolean graceful) {
    HttpClientMetrics metrics = conn.metrics();
    if (METRICS_ENABLED && metrics != null) {
      metrics.disconnected(getMetric());
      setMetric(null);
    }
    super.handleClose(graceful);
  }
}
