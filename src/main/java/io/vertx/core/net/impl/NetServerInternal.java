/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface NetServerInternal extends NetServer {

  @Override
  NetServerInternal connectHandler(@Nullable Handler<NetSocket> handler);

  @Override
  NetServerInternal exceptionHandler(Handler<Throwable> handler);

  SslContextProvider sslContextProvider();

  Future<NetServer> listen(ContextInternal context, SocketAddress localAddress);

}
