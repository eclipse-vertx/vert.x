package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

public class VertxHttp3RequestStreamInboundHandler extends Http3RequestStreamInboundHandler {
  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
    ReferenceCountUtil.release(frame);
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
    System.err.print(frame.content().toString(CharsetUtil.US_ASCII));
    ReferenceCountUtil.release(frame);
  }

  @Override
  protected void channelInputClosed(ChannelHandlerContext ctx) {
    ctx.close();
  }
}
