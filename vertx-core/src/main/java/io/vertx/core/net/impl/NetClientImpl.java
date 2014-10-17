/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClientImpl implements NetClient {

  private static final Logger log = LoggerFactory.getLogger(NetClientImpl.class);

  private final VertxInternal vertx;
  private final NetClientOptions options;
  private final SSLHelper sslHelper;
  private final Map<Channel, NetSocketImpl> socketMap = new ConcurrentHashMap<>();
  private final Closeable closeHook;
  private final ContextImpl creatingContext;
  private boolean closed;

  public NetClientImpl(VertxInternal vertx, NetClientOptions options) {
    this.vertx = vertx;
    this.options = new NetClientOptions(options);
    this.sslHelper = new SSLHelper(options, KeyStoreHelper.create(vertx, options.getKeyStoreOptions()), KeyStoreHelper.create(vertx, options.getTrustStoreOptions()));
    this.closeHook = completionHandler -> {
      NetClientImpl.this.close();
      completionHandler.handle(Future.completedFuture());
    };
    creatingContext = vertx.getContext();
    if (creatingContext != null) {
      if (creatingContext.isMultiThreaded()) {
        throw new IllegalStateException("Cannot use NetClient in a multi-threaded worker verticle");
      }
      creatingContext.addCloseHook(closeHook);
    }
  }

  @Override
  public NetClient connect(int port, String host, final Handler<AsyncResult<NetSocket>> connectHandler) {
    checkClosed();
    connect(port, host, connectHandler, options.getReconnectAttempts());
    return this;
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      for (NetSocket sock : socketMap.values()) {
        sock.close();
      }
      if (creatingContext != null) {
        creatingContext.removeCloseHook(closeHook);
      }
      closed = true;
    }
  }

  private void checkClosed() {
    if (closed) {
      throw new IllegalStateException("Client is closed");
    }
  }

  private void applyConnectionOptions(Bootstrap bootstrap) {
    bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    if (options.getSendBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    if (options.getTrafficClass() != -1) {
      bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());
    bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
  }

  private void connect(final int port, final String host, final Handler<AsyncResult<NetSocket>> connectHandler,
                       final int remainingAttempts) {
    Objects.requireNonNull(host, "No null host accepted");
    Objects.requireNonNull(connectHandler, "No null connectHandler accepted");
    ContextImpl context = vertx.getOrCreateContext();
    sslHelper.checkSSL(vertx);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.getEventLoop());
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslHelper.isSSL()) {
          SslHandler sslHandler = sslHelper.createSslHandler(vertx, true);
          pipeline.addLast("ssl", sslHandler);
        }
        if (sslHelper.isSSL()) {
          // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
          pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
        }
        if (options.getIdleTimeout() > 0) {
          pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
        }
        pipeline.addLast("handler", new VertxNetHandler(vertx, socketMap));
      }
    });

    applyConnectionOptions(bootstrap);
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        final Channel ch = channelFuture.channel();

        if (channelFuture.isSuccess()) {

          if (sslHelper.isSSL()) {
            // TCP connected, so now we must do the SSL handshake

            SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

            io.netty.util.concurrent.Future<Channel> fut = sslHandler.handshakeFuture();
            fut.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<Channel>>() {
              @Override
              public void operationComplete(io.netty.util.concurrent.Future<Channel> future) throws Exception {
                if (future.isSuccess()) {
                  connected(context, ch, connectHandler);
                } else {
                  failed(context, ch, future.cause(), connectHandler);
                }
              }
            });
          } else {
            connected(context, ch, connectHandler);
          }
        } else {
          if (remainingAttempts > 0 || remainingAttempts == -1) {
            context.execute(() -> {
              log.debug("Failed to create connection. Will retry in " + options.getReconnectInterval() + " milliseconds");
              //Set a timer to retry connection
              vertx.setTimer(options.getReconnectInterval(), tid -> {
                connect(port, host, connectHandler, remainingAttempts == -1 ? remainingAttempts : remainingAttempts
                    - 1);
              });
            }, true);
          } else {
            failed(context, ch, channelFuture.cause(), connectHandler);
          }
        }
      }
    });
  }

  private void connected(ContextImpl context, Channel ch, Handler<AsyncResult<NetSocket>> connectHandler) {
    context.execute(() -> doConnected(context, ch, connectHandler), true);
  }

  private void doConnected(ContextImpl context, Channel ch, final Handler<AsyncResult<NetSocket>> connectHandler) {
    NetSocketImpl sock = new NetSocketImpl(vertx, ch, context, sslHelper, true);
    socketMap.put(ch, sock);
    connectHandler.handle(Future.completedFuture(sock));
  }

  private void failed(ContextImpl context, Channel ch, Throwable t, Handler<AsyncResult<NetSocket>> connectHandler) {
    ch.close();
    context.execute(() -> doFailed(connectHandler, t), true);
  }

  private static void doFailed(Handler<AsyncResult<NetSocket>> connectHandler, Throwable t) {
    connectHandler.handle(Future.completedFuture(t));
  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close();
    super.finalize();
  }
}

