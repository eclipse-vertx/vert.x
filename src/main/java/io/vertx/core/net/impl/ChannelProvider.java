package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.ProxyOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ChannelProvider {

  void connect(VertxInternal vertx,
               Bootstrap bootstrap,
               ProxyOptions options,
               String host,
               int port,
               Handler<AsyncResult<Channel>> channelHandler);

}
