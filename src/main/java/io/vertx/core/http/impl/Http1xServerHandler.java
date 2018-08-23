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

package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.impl.HandlerHolder;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.HttpServerMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1xServerHandler extends VertxHttpHandler<Http1xServerConnection> {

  private final SSLHelper sslHelper;
  private final HttpServerOptions options;
  private final String serverOrigin;
  private final HttpServerMetrics metrics;
  private final HandlerHolder<HttpHandlers> holder;

  public Http1xServerHandler(SSLHelper sslHelper, HttpServerOptions options, String serverOrigin, HandlerHolder<HttpHandlers> holder, HttpServerMetrics metrics) {
    this.holder = holder;
    this.metrics = metrics;
    this.sslHelper = sslHelper;
    this.options = options;
    this.serverOrigin = serverOrigin;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    Http1xServerConnection conn = new Http1xServerConnection(holder.context.owner(),
      sslHelper,
      options,
      ctx,
      holder.context,
      serverOrigin,
      holder.handler,
      metrics);
    setConnection(conn);
    if (metrics != null) {
      holder.context.executeFromIO(v -> {
        conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
      });
    }
  }
}
