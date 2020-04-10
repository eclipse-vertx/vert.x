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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * An handler that waits for SSL handshake completion and dispatch it to the server handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SslHandshakeCompletionHandler extends ChannelInboundHandlerAdapter {

  static AttributeKey<String> SERVER_NAME_ATTR = AttributeKey.valueOf("sniServerName");
  private final Promise<Void> promise;

  public SslHandshakeCompletionHandler(Promise<Void> promise) {
    this.promise = promise;
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof SniCompletionEvent) {
      // Shall we care ?
      SniCompletionEvent completion = (SniCompletionEvent) evt;
      Attribute<String> val = ctx.channel().attr(SERVER_NAME_ATTR);
      val.set(completion.hostname());
    } else if (evt instanceof SslHandshakeCompletionEvent) {
      SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
      if (completion.isSuccess()) {
        ctx.pipeline().remove(this);
        promise.setSuccess(null);
      } else {
        promise.tryFailure(completion.cause());
      }
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // Ignore these exception as they will be reported to the handler
    // the handshake will ultimately fail or succeed
  }
}
