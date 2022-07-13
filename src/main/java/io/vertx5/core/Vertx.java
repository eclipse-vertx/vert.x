package io.vertx5.core;

import io.netty5.channel.EventLoop;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.group.ChannelGroup;
import io.netty5.channel.group.DefaultChannelGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.vertx.core.Context;
import io.vertx.core.impl.ContextInternal;
import io.vertx5.core.http.HttpClient;
import io.vertx5.core.http.HttpServer;
import io.vertx5.core.impl.EventLoopContext;
import io.vertx5.core.impl.VertxThread;
import io.vertx5.core.impl.transport.Transport;
import io.vertx5.core.net.NetClient;
import io.vertx5.core.net.NetServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Vertx {

  public static Vertx vertx() {
    return vertx(new VertxOptions());
  }

  public static Vertx vertx(VertxOptions options) {
    return new Vertx(options);
  }

  private final Transport transport;
  private final MultithreadEventLoopGroup acceptorEventLoopGroup;
  private final MultithreadEventLoopGroup eventLoopGroup;
  private final ChannelGroup channelGroup;

  private Vertx(VertxOptions options) {

    Transport transport = Transport.transport();

    MultithreadEventLoopGroup acceptorEventLoopGroup = new MultithreadEventLoopGroup(1, runnable -> {
      return new VertxThread(runnable, "vert.x-acceptor-thread-");
    }, NioHandler.newFactory());

    MultithreadEventLoopGroup eventLoopGroup = new MultithreadEventLoopGroup(options.getEventLoopPoolSize(), runnable -> {
      return new VertxThread(runnable, "vertx-eventloop-thread-");
    }, NioHandler.newFactory());

    this.transport = transport;
    this.acceptorEventLoopGroup = acceptorEventLoopGroup;
    this.eventLoopGroup = eventLoopGroup;
    this.channelGroup = new DefaultChannelGroup(acceptorEventLoopGroup.next());
  }

  public Context getOrCreateContext() {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      VertxThread vertxThread = (VertxThread) current;
      return vertxThread.context();
    }
    EventLoop eventLoop = eventLoopGroup.next();
    return new EventLoopContext(this, eventLoop);
  }

  public MultithreadEventLoopGroup acceptorEventLoopGroup() {
    return acceptorEventLoopGroup;
  }

  public ChannelGroup channelGroup() {
    return channelGroup;
  }

  public NetServer createNetServer() {
    return new NetServer((ContextInternal) getOrCreateContext());
  }

  public NetClient createNetClient() {
    return new NetClient(this);
  }

  public HttpServer createHttpServer() {
    return new HttpServer((ContextInternal) getOrCreateContext());
  }

  public HttpClient createHttpClient() {
    return new HttpClient(this);
  }

  public void close() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    channelGroup.close().addListener(f -> latch.countDown());
    latch.await(20, TimeUnit.SECONDS);
    acceptorEventLoopGroup.shutdownGracefully();
    eventLoopGroup.shutdownGracefully();
  }
}
