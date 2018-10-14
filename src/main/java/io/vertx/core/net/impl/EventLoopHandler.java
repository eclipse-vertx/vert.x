package io.vertx.core.net.impl;

import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxThread;

import java.util.function.Function;

class EventLoopHandler<C extends ConnectionBase> extends VertxHandler<C> {

  EventLoopHandler(ContextInternal context, Function<ChannelHandlerContext, C> connectionFactory) {
    super(context, connectionFactory);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    conn.endReadAndFlush();
  }

  @Override
  public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
    VertxThread current = context.before();
    try {
      conn.handleRead(msg);
    } catch (Throwable t) {
      context.reportException(t);
    } finally {
      context.after(current);
    }
  }
}
