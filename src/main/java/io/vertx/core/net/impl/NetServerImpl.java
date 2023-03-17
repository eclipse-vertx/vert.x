/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.streams.ReadStream;

import java.util.function.BiConsumer;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetServerImpl extends TCPServerBase implements Closeable, MetricsProvider, NetServer {

  private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);

  private final NetSocketStream connectStream = new NetSocketStream();
  private long demand = Long.MAX_VALUE;
  private Handler<NetSocket> handler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;

  public NetServerImpl(VertxInternal vertx, NetServerOptions options) {
    super(vertx, options);
  }

  private synchronized void pauseAccepting() {
    demand = 0L;
  }

  private synchronized void resumeAccepting() {
    demand = Long.MAX_VALUE;
  }

  private synchronized void fetchAccepting(long amount) {
    if (amount > 0L) {
      demand += amount;
      if (demand < 0L) {
        demand = Long.MAX_VALUE;
      }
    }
  }

  protected synchronized boolean accept() {
    boolean accept = demand > 0L;
    if (accept && demand != Long.MAX_VALUE) {
      demand--;
    }
    return accept;
  }

  @Override
  public synchronized Handler<NetSocket> connectHandler() {
    return handler;
  }

  @Override
  public synchronized NetServer connectHandler(Handler<NetSocket> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set connectHandler when server is listening");
    }
    this.handler = handler;
    return this;
  }

  @Override
  public synchronized NetServer exceptionHandler(Handler<Throwable> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set exceptionHandler when server is listening");
    }
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  protected TCPMetrics<?> createMetrics(SocketAddress localAddress) {
    VertxMetrics vertxMetrics = vertx.metricsSPI();
    if (vertxMetrics != null) {
      return vertxMetrics.createNetServerMetrics(options, localAddress);
    } else {
      return null;
    }
  }

  public Future<Void> close() {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Void> promise = context.promise();
    close(promise);
    return promise.future();
  }

  @Override
  protected BiConsumer<Channel, SslChannelProvider> childHandler(ContextInternal context, SocketAddress socketAddress) {
    return new NetServerWorker(context, handler, exceptionHandler);
  }

  @Override
  public synchronized Future<NetServer> listen(SocketAddress localAddress) {
    if (localAddress == null) {
      throw new NullPointerException("No null bind local address");
    }
    if (handler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    return bind(localAddress).map(this);
  }

  @Override
  public Future<NetServer> listen() {
    return listen(options.getPort(), options.getHost());
  }

  @Override
  public ReadStream<NetSocket> connectStream() {
    return connectStream;
  }

  @Override
  public synchronized void close(Promise<Void> completion) {
    super.close(completion);
    Handler<Void> handler = endHandler;
    if (endHandler != null) {
      endHandler = null;
      completion.future().onComplete(ar -> handler.handle(null));
    }
  }

  public boolean isClosed() {
    return !isListening();
  }

  private class NetServerWorker implements BiConsumer<Channel, SslChannelProvider> {

    private final ContextInternal context;
    private final Handler<NetSocket> connectionHandler;
    private final Handler<Throwable> exceptionHandler;

    NetServerWorker(ContextInternal context, Handler<NetSocket> connectionHandler, Handler<Throwable> exceptionHandler) {
      this.context = context;
      this.connectionHandler = connectionHandler;
      this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void accept(Channel ch, SslChannelProvider sslChannelProvider) {
      if (!NetServerImpl.this.accept()) {
        ch.close();
        return;
      }
      if (HAProxyMessageCompletionHandler.canUseProxyProtocol(options.isUseProxyProtocol())) {
        IdleStateHandler idle;
        io.netty.util.concurrent.Promise<Channel> p = ch.eventLoop().newPromise();
        ch.pipeline().addLast(new HAProxyMessageDecoder());
        if (options.getProxyProtocolTimeout() > 0) {
          ch.pipeline().addLast("idle", idle = new IdleStateHandler(0, 0, options.getProxyProtocolTimeout(), options.getProxyProtocolTimeoutUnit()));
        } else {
          idle = null;
        }
        ch.pipeline().addLast(new HAProxyMessageCompletionHandler(p));
        p.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) future -> {
          if (future.isSuccess()) {
            if (idle != null) {
              ch.pipeline().remove(idle);
            }
            configurePipeline(future.getNow(), sslChannelProvider);
          } else {
            //No need to close the channel.HAProxyMessageDecoder already did
            handleException(future.cause());
          }
        });
      } else {
        configurePipeline(ch, sslChannelProvider);
      }
    }

    private void configurePipeline(Channel ch, SslChannelProvider sslChannelProvider) {
      if (options.isSsl()) {
        ch.pipeline().addLast("ssl", sslChannelProvider.createServerHandler());
        ChannelPromise p = ch.newPromise();
        ch.pipeline().addLast("handshaker", new SslHandshakeCompletionHandler(p));
        p.addListener(future -> {
          if (future.isSuccess()) {
            connected(ch, sslChannelProvider);
          } else {
            handleException(future.cause());
          }
        });
      } else {
        connected(ch, sslChannelProvider);
      }
    }

    private void handleException(Throwable cause) {
      if (exceptionHandler != null) {
        context.emit(v -> exceptionHandler.handle(cause));
      }
    }

    private void connected(Channel ch, SslChannelProvider sslChannelProvider) {
      NetServerImpl.this.initChannel(ch.pipeline(), options.isSsl());
      TCPMetrics<?> metrics = getMetrics();
      VertxHandler<NetSocketImpl> handler = VertxHandler.create(ctx -> new NetSocketImpl(context, ctx, sslChannelProvider, metrics, options.isRegisterWriteHandler()));
      handler.removeHandler(NetSocketImpl::unregisterEventBusHandler);
      handler.addHandler(conn -> {
        if (metrics != null) {
          conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
        }
        conn.registerEventBusHandler();
        context.emit(conn, connectionHandler::handle);
      });
      ch.pipeline().addLast("handler", handler);
    }
  }

  protected void initChannel(ChannelPipeline pipeline, boolean ssl) {
    if (options.getLogActivity()) {
      pipeline.addLast("logging", new LoggingHandler(options.getActivityLogDataFormat()));
    }
    if (ssl || !vertx.transport().supportFileRegion()) {
      // only add ChunkedWriteHandler when SSL is enabled or FileRegion isn't supported
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    int idleTimeout = options.getIdleTimeout();
    int readIdleTimeout = options.getReadIdleTimeout();
    int writeIdleTimeout = options.getWriteIdleTimeout();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, options.getIdleTimeoutUnit()));
    }
  }

  /*
          Needs to be protected using the NetServerImpl monitor as that protects the listening variable
          In practice synchronized overhead should be close to zero assuming most access is from the same thread due
          to biased locks
        */
  private class NetSocketStream implements ReadStream<NetSocket> {



    @Override
    public NetSocketStream handler(Handler<NetSocket> handler) {
      connectHandler(handler);
      return this;
    }

    @Override
    public NetSocketStream pause() {
      pauseAccepting();
      return this;
    }

    @Override
    public NetSocketStream resume() {
      resumeAccepting();
      return this;
    }

    @Override
    public ReadStream<NetSocket> fetch(long amount) {
      fetchAccepting(amount);
      return this;
    }

    @Override
    public NetSocketStream endHandler(Handler<Void> handler) {
      synchronized (NetServerImpl.this) {
        endHandler = handler;
        return this;
      }
    }

    @Override
    public NetSocketStream exceptionHandler(Handler<Throwable> handler) {
      // Should we use it in the server close exception handler ?
      return this;
    }
  }
}
