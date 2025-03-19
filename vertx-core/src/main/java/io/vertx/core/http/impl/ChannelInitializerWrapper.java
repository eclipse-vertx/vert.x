package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.vertx.core.Handler;

class ChannelInitializerWrapper<C extends Channel> extends ChannelInitializer<C> {
  private final Handler<C> handler;

  public ChannelInitializerWrapper(Handler<C> handler) {
    this.handler = handler;
  }

  @Override
  protected void initChannel(C ch) {
    handler.handle(ch);
  }
}
