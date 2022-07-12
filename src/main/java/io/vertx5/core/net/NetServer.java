package io.vertx5.core.net;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx5.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;

public class NetServer {

  private final ContextInternal context;
  private Handler<NetSocket> connectHandler;
  private final Promise<Void> closeFuture;

  public NetServer(ContextInternal context) {
    this.context = context;
    this.closeFuture = context.promise();
  }

  public synchronized NetServer connectHandler(Handler<NetSocket> handler) {
    connectHandler = handler;
    return this;
  }

  public synchronized Handler<NetSocket> connectHandler() {
    return connectHandler;
  }

  public Future<Void> closeFuture() {
    return closeFuture.future();
  }

  public Future<SocketAddress> listen(int port, String host) {
    Promise<SocketAddress> promise = context.promise();
    MultithreadEventLoopGroup acceptor = context.<Vertx>owner().acceptorEventLoopGroup();
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(acceptor, context.nettyEventLoop());
    bootstrap.channel(NioServerSocketChannel.class);
    bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel ch) {
        Handler<NetSocket> handler = connectHandler();
        if (handler == null) {
          ch.close();
          return;
        }
        NetSocket so = new NetSocket(context, ch);
        ch.pipeline().addLast("vertx", so.handler);
        context.emit(so, handler);
      }
    });
    io.netty5.util.concurrent.Future<Channel> res = bootstrap.bind(host, port);
    res.addListener(future -> {
     if (future.isSuccess()) {
       Channel ch = future.getNow();
       context.<Vertx>owner()
         .channelGroup()
         .add(ch);
       ch.closeFuture().addListener(f -> closeFuture.complete());
       promise.complete(SocketAddress.inetSocketAddress(port, host));
     } else {
       promise.fail(future.cause());
     }
    });
    return promise.future();
  }
}
