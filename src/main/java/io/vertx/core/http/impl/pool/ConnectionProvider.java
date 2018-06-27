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

package io.vertx.core.http.impl.pool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

/**
 * Provides how the connection manager interacts its connections.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ConnectionProvider<C> {

  /**
   * Connect to the server.
   *
   * @param listener the listener
   * @param context the context to use for the connection
   * @param resultHandler the handler notified with the connection success or failure
   */
  void connect(ConnectionListener<C> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<C>>> resultHandler);

  /**
   * Callback before the connection becomes used (at least one usage).
   *
   * @param conn the connection
   */
  void activate(C conn);

  /**
   * Callback before the connection becomes unused (no usage).
   *
   * @param conn the connection
   */
  void deactivate(C conn);

  /**
   * Close a connection.
   *
   * @param conn the connection
   */
  void close(C conn);

}
