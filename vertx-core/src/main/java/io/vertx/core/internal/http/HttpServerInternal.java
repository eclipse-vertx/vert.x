/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.http;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.MetricsProvider;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpServerInternal extends HttpServer, MetricsProvider {

  boolean isClosed();


  Future<HttpServer> listen(ContextInternal context);
  Future<HttpServer> listen(ContextInternal context, SocketAddress address);

  default HttpServerInternal unwrap() {
    return this;
  }

}
