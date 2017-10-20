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
