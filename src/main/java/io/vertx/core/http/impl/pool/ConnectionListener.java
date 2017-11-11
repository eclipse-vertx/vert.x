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

/**
 * The listener is used by the {@link ConnectionProvider} to interact with the connection manager.
 */
public interface ConnectionListener<C> {

  /**
   * The connection concurrency changed.
   *
   * @param conn the connection
   */
  void concurrencyChanged(C conn);

  /**
   * The connection can be recycled.
   *
   * @param conn the connection
   */
  void recycle(C conn);

  /**
   * The connection is closed.
   *
   * @param conn the connection
   */
  void closed(C conn);

}
