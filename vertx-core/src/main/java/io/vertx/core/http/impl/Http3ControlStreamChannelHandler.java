package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.http3.DefaultHttp3GoAwayFrame;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.DefaultHttp3UnknownFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

class Http3ControlStreamChannelHandler extends ChannelInboundHandlerAdapter {
  private static final InternalLogger logger =
    InternalLoggerFactory.getInstance(Http3ControlStreamChannelHandler.class);

  private final VertxHttp3ConnectionHandler handler;
  private final String agentType;

  public Http3ControlStreamChannelHandler(VertxHttp3ConnectionHandler handler) {
    this.handler = handler;
    this.agentType = handler.getAgentType();
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    logger.debug("{} - Received event for channelId: {}, event: {}", agentType, ctx.channel().id(),
      evt.getClass().getSimpleName());
    super.userEventTriggered(ctx, evt);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    logger.debug("{} - channelRead() called with msg type: {}", agentType, msg.getClass().getSimpleName());

    if (msg instanceof DefaultHttp3SettingsFrame) {
      DefaultHttp3SettingsFrame http3SettingsFrame = (DefaultHttp3SettingsFrame) msg;
      handler.onSettingsRead(ctx, http3SettingsFrame);
//        VertxHttp3ConnectionHandler.this.connection.updateHttpSettings(HttpUtils.toVertxSettings(http3SettingsFrame));
      ReferenceCountUtil.release(msg);
    } else if (msg instanceof DefaultHttp3GoAwayFrame) {
      super.channelRead(ctx, msg);
      DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
      handler.onGoAwayReceived(http3GoAwayFrame);
      ReferenceCountUtil.release(msg);
    } else if (msg instanceof DefaultHttp3UnknownFrame) {
      if (logger.isDebugEnabled()) {
        logger.debug("{} - Received unknownFrame : {}", agentType);
      }
      ReferenceCountUtil.release(msg);
      super.channelRead(ctx, msg);
    } else {
      super.channelRead(ctx, msg);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    logger.debug("{} - ChannelReadComplete called for channelId: {}, streamId: {}", agentType,
      ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId());

    handler.onSettingsReadDone();
    super.channelReadComplete(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    logger.debug("{} - Caught exception on channelId : {}!", agentType, ctx.channel().id(), cause);
    super.exceptionCaught(ctx, cause);
  }

}
