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
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.*;
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
  private Handler<NetSocket> registeredHandler;
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

  protected void initChannel(ChannelPipeline pipeline) {
    if (options.getLogActivity()) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    if (sslHelper.isSSL()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
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
  public Future<NetServer> listen(int port, String host) {
    return listen(SocketAddress.inetSocketAddress(port, host));
  }

  @Override
  public NetServer listen(int port, String host, Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(SocketAddress.inetSocketAddress(port, host), listenHandler);
  }

  @Override
  public Future<NetServer> listen(int port) {
    return listen(port, "0.0.0.0");
  }

  @Override
  public NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(port, "0.0.0.0", listenHandler);
  }

  @Override
  public synchronized Future<NetServer> listen(SocketAddress localAddress) {
    if (handler == null) {
      throw new IllegalStateException("Set connect handler first");
    }

    ContextInternal listenContext = vertx.getOrCreateContext();
    registeredHandler = handler;

    io.netty.util.concurrent.Future<Channel> bindFuture = listen(localAddress, listenContext, new NetServerWorker(listenContext, handler, exceptionHandler));

    // just add it to the future so it gets notified once the bind is complete
    Promise<NetServer> promise = listenContext.promise();
    bindFuture.addListener(res -> {
      if (res.isSuccess()) {
        promise.complete(this);
      } else {
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  @Override
  public synchronized NetServer listen(SocketAddress localAddress, Handler<AsyncResult<NetServer>> listenHandler) {
    if (listenHandler == null) {
      listenHandler = res -> {
        if (res.failed()) {
          // No handler - log so user can see failure
          log.error("Failed to listen", res.cause());
        }
      };
    }
    listen(localAddress).onComplete(listenHandler);
    return this;
  }

  @Override
  public synchronized Future<NetServer> listen() {
    return listen(options.getPort(), options.getHost());
  }

  @Override
  public synchronized NetServer listen(Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(options.getPort(), options.getHost(), listenHandler);
  }

  @Override
  public ReadStream<NetSocket> connectStream() {
    return connectStream;
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Void> promise = context.promise();
    close(promise);
    promise.future().onComplete(completionHandler);
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

  private class NetServerWorker implements Handler<Channel> {

    private final ContextInternal context;
    private final Handler<NetSocket> connectionHandler;
    private final Handler<Throwable> exceptionHandler;

    NetServerWorker(ContextInternal context, Handler<NetSocket> connectionHandler, Handler<Throwable> exceptionHandler) {
      this.context = context;
      this.connectionHandler = connectionHandler;
      this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void handle(Channel ch) {
      if (!accept()) {
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
            configurePipeline(future.getNow());
          } else {
            //No need to close the channel.HAProxyMessageDecoder already did
            handleException(future.cause());
          }
        });
      } else {
        configurePipeline(ch);
      }
    }

    private void configurePipeline(Channel ch) {
      if (sslHelper.isSSL()) {
        if (options.isSni()) {
          SniHandler sniHandler = new SniHandler(sslHelper.serverNameMapper(vertx));
          ch.pipeline().addLast("ssl", sniHandler);
        } else {
          SslHandler sslHandler = new SslHandler(sslHelper.createEngine(vertx));
          sslHandler.setHandshakeTimeout(sslHelper.getSslHandshakeTimeout(), sslHelper.getSslHandshakeTimeoutUnit());
          ch.pipeline().addLast("ssl", sslHandler);
        }
        ChannelPromise p = ch.newPromise();
        ch.pipeline().addLast("handshaker", new SslHandshakeCompletionHandler(p));
        p.addListener(future -> {
          if (future.isSuccess()) {
            connected(ch);
          } else {
            handleException(future.cause());
          }
        });
      } else {
        connected(ch);
      }
    }

    private void handleException(Throwable cause) {
      if (exceptionHandler != null) {
        context.emit(v -> exceptionHandler.handle(cause));
      }
    }

    private void connected(Channel ch) {
      NetServerImpl.this.initChannel(ch.pipeline());
      TCPMetrics<?> metrics = getMetrics();
      VertxHandler<NetSocketImpl> handler = VertxHandler.create(ctx -> new NetSocketImpl(context, ctx, sslHelper, metrics));
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
