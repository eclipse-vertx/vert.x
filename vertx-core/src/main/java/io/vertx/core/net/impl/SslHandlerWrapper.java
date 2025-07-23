package io.vertx.core.net.impl;

import io.netty.channel.*;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import java.net.SocketAddress;
import java.util.concurrent.Executor;

class SslHandlerWrapper extends SslHandler implements ChannelInboundHandler {
  private final ChannelDuplexHandler delegate;

  SslHandlerWrapper(SSLEngine engine, Executor delegatedTaskExecutor, ChannelHandler quicSslHandler) {
    super(engine, delegatedTaskExecutor);
    delegate = (ChannelDuplexHandler) quicSslHandler;
  }

  @Override
  public void channelRegistered(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.channelRegistered(channelHandlerContext);
  }

  @Override
  public void channelUnregistered(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.channelUnregistered(channelHandlerContext);
  }

  @Override
  public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.channelActive(channelHandlerContext);
  }

  @Override
  public void channelInactive(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.channelInactive(channelHandlerContext);
  }

  @Override
  public void channelRead(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
    delegate.channelRead(channelHandlerContext, o);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.channelReadComplete(channelHandlerContext);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
    delegate.userEventTriggered(channelHandlerContext, o);
  }

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.channelWritabilityChanged(channelHandlerContext);
  }

  @Override
  public void bind(ChannelHandlerContext channelHandlerContext, SocketAddress socketAddress, ChannelPromise channelPromise) throws Exception {
    delegate.bind(channelHandlerContext, socketAddress, channelPromise);
  }

  @Override
  public void connect(ChannelHandlerContext channelHandlerContext, SocketAddress socketAddress, SocketAddress socketAddress1, ChannelPromise channelPromise) throws Exception {
    delegate.connect(channelHandlerContext, socketAddress, socketAddress1, channelPromise);
  }

  @Override
  public void disconnect(ChannelHandlerContext channelHandlerContext, ChannelPromise channelPromise) throws Exception {
    delegate.disconnect(channelHandlerContext, channelPromise);
  }

  @Override
  public void close(ChannelHandlerContext channelHandlerContext, ChannelPromise channelPromise) throws Exception {
    delegate.close(channelHandlerContext, channelPromise);
  }

  @Override
  public void deregister(ChannelHandlerContext channelHandlerContext, ChannelPromise channelPromise) throws Exception {
    delegate.deregister(channelHandlerContext, channelPromise);
  }

  @Override
  public void read(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.read(channelHandlerContext);
  }

  @Override
  public void write(ChannelHandlerContext channelHandlerContext, Object o, ChannelPromise channelPromise) throws Exception {
    delegate.write(channelHandlerContext, o, channelPromise);
  }

  @Override
  public void flush(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.flush(channelHandlerContext);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.handlerAdded(channelHandlerContext);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
    delegate.exceptionCaught(channelHandlerContext, throwable);
  }

  @Override
  public void handlerRemoved0(ChannelHandlerContext channelHandlerContext) throws Exception {
    delegate.handlerRemoved(channelHandlerContext);
  }
}
