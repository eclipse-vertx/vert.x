package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3Exception;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicException;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.spi.metrics.ClientMetrics;

public class Http3ClientConnection {
  public static Http3ClientConnectionHandler createHttp3ConnectionHandler(
    HttpClientImpl client, ClientMetrics metrics, EventLoopContext context, boolean b, Object metric) {

    Http3RequestStreamInboundHandler http3RequestStreamInboundHandler = new Http3RequestStreamInboundHandler() {
      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
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
    };
    Http3ClientConnectionHandler clientConnectionHandler =
      new Http3ClientConnectionHandler(null, null, null, null, false);

    return clientConnectionHandler;
  }
}
