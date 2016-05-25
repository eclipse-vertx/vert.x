package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * The logic for connecting to an host, this implementations performs a connection
 * to the host after resolving its internet address.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ChannelProvider {

  public static final ChannelProvider INSTANCE = new ChannelProvider();

  protected ChannelProvider() {
  }

  static final Logger log = LoggerFactory.getLogger(NetClientImpl.class);

  public void connect(VertxInternal vertx,
                 Bootstrap bootstrap,
                 ProxyOptions options,
                 String host,
                 int port,
                 Handler<AsyncResult<Channel>> channelHandler) {
    try {
      doConnect(vertx, bootstrap, options, host, port, channelHandler);
    } catch (NoClassDefFoundError e) {
      if (e.getMessage().contains("io/netty/handler/proxy")) {
        log.warn("Dependency io.netty:netty-handler-proxy missing - check your classpath");
        channelHandler.handle(Future.failedFuture(e));
      }
    }
  }

  protected void doConnect(VertxInternal vertx,
                 Bootstrap bootstrap,
                 ProxyOptions options,
                 String host,
                 int port,
                 Handler<AsyncResult<Channel>> channelHandler) {
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
      }
    });
    AsyncResolveBindConnectHelper future = AsyncResolveBindConnectHelper.doConnect(vertx, port, host, bootstrap);
    future.addListener(res -> {
      if (res.succeeded()) {
        channelHandler.handle(Future.succeededFuture(res.result()));
      } else {
        channelHandler.handle(Future.failedFuture(res.cause()));
      }
    });
  }
}
