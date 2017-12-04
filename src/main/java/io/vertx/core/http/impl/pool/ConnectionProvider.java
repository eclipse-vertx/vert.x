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
