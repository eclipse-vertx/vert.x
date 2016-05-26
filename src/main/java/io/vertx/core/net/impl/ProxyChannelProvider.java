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
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A channel provider that connects via a Proxy : HTTP or Socks
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyChannelProvider extends ChannelProvider {

  public static final ChannelProvider INSTANCE = new ProxyChannelProvider();

  private ProxyChannelProvider() {
  }

  @Override
  protected void doConnect(VertxInternal vertx, Bootstrap bootstrap, ProxyOptions options, String host, int port,
                           Handler<Channel> channelInitializer, Handler<AsyncResult<Channel>> channelHandler) {

    final String proxyHost = options.getProxyHost();
    final int proxyPort = options.getProxyPort();
    final String proxyUsername = options.getProxyUsername();
    final String proxyPassword = options.getProxyPassword();
    final ProxyType proxyType = options.getProxyType();

    vertx.resolveAddress(proxyHost, dnsRes -> {
      if (dnsRes.succeeded()) {
        InetAddress address = dnsRes.result();
        InetSocketAddress proxyAddr = new InetSocketAddress(address, proxyPort);
        ProxyHandler proxy;

        switch (proxyType) {
          default:
          case HTTP:
            proxy = proxyUsername != null && proxyPassword != null
                ? new HttpProxyHandler(proxyAddr, proxyUsername, proxyPassword) : new HttpProxyHandler(proxyAddr);
            break;
          case SOCKS5:
            proxy = proxyUsername != null && proxyPassword != null
                ? new Socks5ProxyHandler(proxyAddr, proxyUsername, proxyPassword) : new Socks5ProxyHandler(proxyAddr);
            break;
          case SOCKS4:
            // apparently SOCKS4 only supports a username?
            proxy = proxyUsername != null ? new Socks4ProxyHandler(proxyAddr, proxyUsername)
                : new Socks4ProxyHandler(proxyAddr);
            break;
        }

        bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        InetSocketAddress targetAddress = InetSocketAddress.createUnresolved(host, port);

        bootstrap.handler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addFirst("proxy", proxy);
            pipeline.addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof ProxyConnectionEvent) {
                  pipeline.remove(proxy);
                  pipeline.remove(this);
                  channelInitializer.handle(ch);
                  channelHandler.handle(Future.succeededFuture(ch));
                }
                ctx.fireUserEventTriggered(evt);
              }
            });
          }
        });
        ChannelFuture future = bootstrap.connect(targetAddress);

        future.addListener(res -> {
          if (!res.isSuccess()) {
            channelHandler.handle(Future.failedFuture(res.cause()));
          }
        });
      } else {
        channelHandler.handle(Future.failedFuture(dnsRes.cause()));
      }
    });
  }
}
