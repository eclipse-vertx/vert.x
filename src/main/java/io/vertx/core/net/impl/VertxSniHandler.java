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
package io.vertx.core.net.impl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.Mapping;
import io.netty.util.concurrent.ScheduledFuture;

import javax.net.ssl.SSLException;
import java.util.concurrent.TimeUnit;

/**
 * Extend the {@code SniHandler} to support handshake timeout
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxSniHandler extends SniHandler {

  private final long handshakeTimeoutMillis;
  private ScheduledFuture<?> timeoutFuture;

  public VertxSniHandler(Mapping<? super String, ? extends SslContext> mapping, long handshakeTimeoutMillis) {
    super(mapping);

    this.handshakeTimeoutMillis = handshakeTimeoutMillis;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    if (handshakeTimeoutMillis > 0) {
      // We assume to always be added in an active channel
      assert(ctx.channel().isActive());
      timeoutFuture = ctx.executor().schedule(() -> {
        SSLException exception = new SSLException("handshake timed out after " + handshakeTimeoutMillis + "ms");
        ctx.fireUserEventTriggered(new SniCompletionEvent(exception));
        ctx.close();
      }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
    }
    super.handlerAdded(ctx);
  }

  @Override
  protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
    if (timeoutFuture != null) {
      timeoutFuture.cancel(false);
    }
    SslHandler sslHandler = super.newSslHandler(context, allocator);
    sslHandler.setHandshakeTimeout(handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
    return sslHandler;
  }
}
