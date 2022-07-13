package io.vertx5.core.net;


import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.util.AttributeKey;
import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx5.core.Vertx;

public class NetClient {

  private Vertx vertx;

  public NetClient(Vertx vertx) {
    this.vertx = vertx;
  }

  private static final AttributeKey<NetSocket> NETSOCKET_KEY = AttributeKey.valueOf("abc");

  public Future<NetSocket> connect(int port, String host) {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    PromiseInternal<NetSocket> promise = ctx.promise();
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(ctx.nettyEventLoop())
      .channel(NioSocketChannel.class)
      .option(ChannelOption.TCP_NODELAY, true)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        public void initChannel(SocketChannel ch) throws Exception {
          ChannelPipeline p = ch.pipeline();
          NetSocket so = new NetSocket(ctx, ch);
          ch.attr(NETSOCKET_KEY).set(so);
          p.addLast("vertx", so.handler);
        }
      });
    bootstrap.connect(host, port).addListener(future -> {
      if (future.isSuccess()) {
        Channel ch = future.getNow();
        vertx.channelGroup().add(ch);
        promise.complete(ch.attr(NETSOCKET_KEY).get());
      } else {
        promise.fail(future.cause());
      }
    });
    return promise.future();
  }

}
