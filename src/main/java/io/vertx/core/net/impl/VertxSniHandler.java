/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.vertx.core.impl.VertxInternal;

import javax.net.ssl.SSLEngine;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxSniHandler extends SniHandler {

  public static AttributeKey<String> SERVER_NAME_ATTR = AttributeKey.valueOf("sniServerName");

  private final SSLHelper helper;
  private ChannelHandlerContext context;
  private final Promise<Channel> handshakeFuture;

  public VertxSniHandler(SSLHelper helper, VertxInternal vertx) {
    super(input -> {
      return helper.getContext(vertx, input);
    });
    this.helper = helper;
    this.handshakeFuture = new DefaultPromise<Channel>() {
      @Override
      protected EventExecutor executor() {
        ChannelHandlerContext ctx = context;
        if (ctx == null) {
          throw new IllegalStateException();
        }
        return ctx.executor();
      }
    };
  }

  public Future<Channel> handshakeFuture() {
    return handshakeFuture;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    context = ctx;
  }

  @Override
  protected void replaceHandler(ChannelHandlerContext ctx, String hostname, SslContext sslContext) throws Exception {
    SslHandler sslHandler = null;
    try {
      SSLEngine engine = helper.createEngine(sslContext);
      sslHandler = new SslHandler(engine);
      ctx.pipeline().replace(this, "ssl", sslHandler);
      Future<Channel> fut = sslHandler.handshakeFuture();
      fut.addListener(future -> {
        if (future.isSuccess()) {
          Attribute<String> val = ctx.channel().attr(SERVER_NAME_ATTR);
          val.set(hostname);
          handshakeFuture.setSuccess(ctx.channel());
        } else {
          handshakeFuture.setFailure(future.cause());
        }
      });
      sslHandler = null;
    } finally {
      // Since the SslHandler was not inserted into the pipeline the ownership of the SSLEngine was not
      // transferred to the SslHandler.
      // See https://github.com/netty/netty/issues/5678
      if (sslHandler != null) {
        ReferenceCountUtil.safeRelease(sslHandler.engine());
      }
    }
  }
}
