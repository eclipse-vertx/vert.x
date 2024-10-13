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

import io.vertx.core.Handler;
import io.vertx.core.http.WebSocket;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.impl.VertxConnection;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketImpl extends WebSocketImplBase<WebSocketImpl> implements WebSocket {

  private Handler<Void> evictionHandler;

  public WebSocketImpl(ContextInternal context,
                       VertxConnection conn,
                       boolean supportsContinuation,
                       int maxWebSocketFrameSize,
                       int maxWebSocketMessageSize,
                       boolean registerWebSocketWriteHandlers) {
    super(context, conn, null, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize, registerWebSocketWriteHandlers);
  }

  public void evictionHandler(Handler<Void> evictionHandler) {
    this.evictionHandler = evictionHandler;
  }

  @Override
  void handleConnectionClosed() {
    Handler<Void> h = evictionHandler;
    if (h != null) {
      h.handle(null);
    }
    super.handleConnectionClosed();
  }
}
