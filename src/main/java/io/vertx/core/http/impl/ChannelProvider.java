package io.vertx.core.http.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.VertxInternal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ChannelProvider {

  void connect(VertxInternal vertx,
               Bootstrap bootstrap,
               HttpClientOptions options,
               String host,
               int port,
               Handler<AsyncResult<Channel>> channelHandler);


}
