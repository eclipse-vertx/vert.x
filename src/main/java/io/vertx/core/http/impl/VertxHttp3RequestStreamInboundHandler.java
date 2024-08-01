package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3Exception;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicException;
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

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    super.exceptionCaught(ctx, cause);
  }

  @Override
  protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
    super.handleQuicException(ctx, exception);
  }

  @Override
  protected void handleHttp3Exception(ChannelHandlerContext ctx, Http3Exception exception) {
    super.handleHttp3Exception(ctx, exception);
  }
}
