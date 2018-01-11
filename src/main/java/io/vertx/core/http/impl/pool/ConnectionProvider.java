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

import io.vertx.core.impl.ContextImpl;

/**
 * Provides how the connection manager interacts its connections.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ConnectionProvider<C> {

  /**
   * Connect to the server and signals the {@code listener} the success with {@link ConnectionListener#onConnectSuccess}
   * or the failure with {@link ConnectionListener#onConnectFailure}.
   *
   * @param listener the listener
   * @param context the context to use for the connection
   * @return the initial weight of the connection, which will eventually be corrected when calling the listener
   */
  long connect(ConnectionListener<C> listener, ContextImpl context);

  /**
   * Close a connection.
   *
   * @param conn the connection
   */
  void close(C conn);

}
