package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;

class ExceptionHandlingChannelHandler implements ChannelHandler {
  private final Promise<Channel> channelHandler;

  public ExceptionHandlingChannelHandler(Promise<Channel> channelHandler) {
    this.channelHandler = channelHandler;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
    channelHandler.tryFailure(throwable);
    channelHandlerContext.close();
  }

  @Override
  public void handlerAdded(ChannelHandlerContext channelHandlerContext) {
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext channelHandlerContext) {
  }
}
