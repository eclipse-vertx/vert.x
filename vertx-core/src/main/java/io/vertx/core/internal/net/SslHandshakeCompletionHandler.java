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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.incubator.codec.quic.QuicConnectionCloseEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;

/**
 * A handler that waits for SSL handshake completion and dispatch it to the server handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SslHandshakeCompletionHandler extends ChannelInboundHandlerAdapter {
  private static final Logger log = LoggerFactory.getLogger(SslHandshakeCompletionHandler.class);

  /**
   * The channel attribute providing the SNI server name, this name is set upon handshake completion when available.
   */
  public static AttributeKey<String> SERVER_NAME_ATTR = AttributeKey.valueOf("sniServerName");

  private final Promise<Void> promise;

  public SslHandshakeCompletionHandler(Promise<Void> promise) {
    this.promise = promise;
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof SniCompletionEvent) {
      log.debug("Received event SniCompletionEvent");
      SniCompletionEvent completion = (SniCompletionEvent) evt;
      if (completion.isSuccess()) {
        Attribute<String> val = ctx.channel().attr(SERVER_NAME_ATTR);
        val.set(completion.hostname());
      } else {
        promise.tryFailure(completion.cause());
      }
    } else if (evt instanceof SslHandshakeCompletionEvent) {
      log.debug("Received event SslHandshakeCompletionEvent");
      SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
      if (completion.isSuccess()) {
        ctx.pipeline().remove(this);
        promise.setSuccess(null);
      } else {
        promise.tryFailure(completion.cause());
      }
    } else if (evt instanceof QuicConnectionCloseEvent) {
      log.debug("Received event QuicConnectionCloseEvent");
      QuicConnectionCloseEvent closeEvt = (QuicConnectionCloseEvent) evt;
      if (closeEvt.isTlsError()) {
        promise.tryFailure(new SSLHandshakeException("QUIC connection terminated due to SSL/TLS error"));
      } else {
        ctx.fireUserEventTriggered(evt);
      }
    } else {
      log.debug("Received unhandled event");
      ctx.fireUserEventTriggered(evt);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // Ignore these exception as they will be reported to the handler
    // the handshake will ultimately fail or succeed
  }
}
