package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.DomainNameMapping;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import javax.net.ssl.SSLEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom SNI handler that allows for listening to handshakes and configuring the SSLEngine.
 */
public class VertxSniHandler extends SniHandler {

  private SSLHelper sslHelper;
  private final List<GenericFutureListener<Future<Channel>>> listeners = new ArrayList<>();

  VertxSniHandler(SSLHelper sslHelper, DomainNameMapping<? extends SslContext> mapping) {
    super(mapping);
    this.sslHelper = sslHelper;
  }

  @Override
  protected void replaceHandler(ChannelHandlerContext ctx, String hostname, SslContext sslContext) throws Exception {
    SslHandler sslHandler = null;
    try {
      SSLEngine sslEngine = sslContext.newEngine(ctx.alloc());
      sslHandler = sslHelper.createHandler(sslEngine);
      for (GenericFutureListener<Future<Channel>> listener : listeners) {
          sslHandler.handshakeFuture().addListener(listener);
      }
      ctx.pipeline().replace(this, "ssl", sslHandler);
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

  public void addHandshakeListener(GenericFutureListener<Future<Channel>> listener) {
    listeners.add(listener);
  }
}
