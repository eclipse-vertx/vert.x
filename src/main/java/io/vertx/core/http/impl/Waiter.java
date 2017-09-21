/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.vertx.core.impl.ContextImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Waiter {

  final HttpClientRequestImpl req;
  final ContextImpl context;
  Object metric;

  public Waiter(HttpClientRequestImpl req, ContextImpl context) {
    this.req = req;
    this.context = context;
  }

  /**
   * Handle connection failure.
   *
   * @param failure the failure
   */
  abstract void handleFailure(Throwable failure);

  /**
   * Handle connection success.
   *
   * @param conn the connection
   */
  abstract void handleConnection(HttpClientConnection conn);

  /**
   * Handle connection success.
   *
   * @param stream the stream
   */
  abstract void handleStream(HttpClientStream stream);

  /**
   * @return true if the waiter has been cancelled
   */
  abstract boolean isCancelled();

}
