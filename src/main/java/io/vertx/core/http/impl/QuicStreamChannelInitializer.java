package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.DefaultPromise;
import io.vertx.core.impl.EventLoopContext;

public class QuicStreamChannelInitializer extends ChannelInitializer<QuicStreamChannel> {

  private final DefaultPromise<ChannelHandlerContext> quicStreamChannelInitFuture;
  private ChannelHandlerContext ctx;

  public QuicStreamChannelInitializer(EventLoopContext context) {
    quicStreamChannelInitFuture = new DefaultPromise<>(context.nettyEventLoop());
  }

  @Override
  protected void initChannel(QuicStreamChannel ch) {
    quicStreamChannelInitFuture.setSuccess(ctx);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    this.ctx = ctx;
    super.handlerAdded(ctx);
  }

  public DefaultPromise<ChannelHandlerContext> quicStreamChannelInitFuture() {
    return quicStreamChannelInitFuture;
  }
}
