/*
 * Copyright (c) 2011-20123Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.MetricsProvider;

public interface NetClientInternal extends NetClient, MetricsProvider, Closeable {

  /**
   * Open a socket to the {@code remoteAddress} server.
   *
   * @param connectOptions the connect options
   * @param connectHandler the promise to resolve with the connect result
   * @param context        the socket context
   */
  void connectInternal(ConnectOptions connectOptions,
                       Promise<NetSocket> connectHandler,
                       ContextInternal context);

  Future<Void> closeFuture();

}
