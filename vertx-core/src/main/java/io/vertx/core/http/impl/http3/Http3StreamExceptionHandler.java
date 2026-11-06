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
package io.vertx.core.http.impl.http3;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http3.Http3Exception;
import io.netty.handler.codec.http3.Http3HeadersValidationException;
import io.vertx.core.net.impl.VertxHandler;

/**
 * Absorbs the HTTP/3 stream errors reported by the Netty codec on a request stream and reports them
 * to the stream: the codec terminates the stream with a stream error right after firing the
 * exception, letting the exception propagate would close the stream channel, which sends a clean
 * FIN preempting the stream error.
 */
@ChannelHandler.Sharable
class Http3StreamExceptionHandler extends ChannelInboundHandlerAdapter {

  static final Http3StreamExceptionHandler INSTANCE = new Http3StreamExceptionHandler();

  private Http3StreamExceptionHandler() {
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (cause instanceof Http3Exception || cause instanceof Http3HeadersValidationException) {
      VertxHandler<?> handler = (VertxHandler<?>) ctx.pipeline().get("handler");
      if (handler != null) {
        handler.getConnection().reportException(cause);
        return;
      }
    }
    ctx.fireExceptionCaught(cause);
  }
}
