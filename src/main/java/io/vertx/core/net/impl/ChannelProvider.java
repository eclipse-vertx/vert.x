package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;

/**
 * The logic for connecting to an host, this implementations performs a connection
 * to the host after resolving its internet address.
 *
 * See if we can replace that by a Netty handler sometimes.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ChannelProvider {

  public static final ChannelProvider INSTANCE = new ChannelProvider();

  protected ChannelProvider() {
  }

  public void connect(VertxInternal vertx, Bootstrap bootstrap, ProxyOptions options, SocketAddress remoteAddress,
      Handler<Channel> channelInitializer, Handler<AsyncResult<Channel>> channelHandler) {
    bootstrap.resolver(vertx.nettyAddressResolverGroup());
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channelInitializer.handle(channel);
      }
    });
    ChannelFuture fut = bootstrap.connect(vertx.transport().convert(remoteAddress, false));
    fut.addListener(res -> {
      if (res.isSuccess()) {
        channelHandler.handle(Future.succeededFuture(fut.channel()));
      } else {
        channelHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }
}
