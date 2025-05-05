package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;

import java.net.ConnectException;
import java.net.PortUnreachableException;

class ExceptionHandlingChannelHandler implements ChannelHandler {
  private final Promise<Channel> channelHandler;

  public ExceptionHandlingChannelHandler(Promise<Channel> channelHandler) {
    this.channelHandler = channelHandler;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
    if (throwable instanceof PortUnreachableException) {
      throwable = new VertxConnectException(throwable);
    }
    channelHandler.tryFailure(throwable);
    channelHandlerContext.close();
  }

  @Override
  public void handlerAdded(ChannelHandlerContext channelHandlerContext) {
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext channelHandlerContext) {
  }

  private static class VertxConnectException extends ConnectException {
    public VertxConnectException(Throwable cause) {
      super(cause.getMessage());
      initCause(cause);
    }
  }
}
