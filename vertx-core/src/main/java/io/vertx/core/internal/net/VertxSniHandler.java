/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.net;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsyncMapping;
import io.vertx.core.net.HostAndPort;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Extend the {@code SniHandler} to support delegated  task executor
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxSniHandler extends SniHandler {

  private final Executor delegatedTaskExec;
  private final HostAndPort remoteAddress;

  public VertxSniHandler(AsyncMapping<? super String, ? extends SslContext> mapping, long handshakeTimeoutMillis, Executor delegatedTaskExec,
      HostAndPort remoteAddress) {
    super(mapping, handshakeTimeoutMillis);

    this.delegatedTaskExec = delegatedTaskExec;
    this.remoteAddress = remoteAddress;
  }

  @Override
  protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
    SslHandler sslHandler;
    if (remoteAddress != null) {
      sslHandler = context.newHandler(allocator, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    } else {
      sslHandler = context.newHandler(allocator, delegatedTaskExec);
    }
    sslHandler.setHandshakeTimeout(handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
    return sslHandler;
  }
}
