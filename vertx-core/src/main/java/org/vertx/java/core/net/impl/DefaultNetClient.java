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

package org.vertx.java.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.Closeable;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetClientOptions;
import org.vertx.java.core.net.NetSocket;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultNetClient implements NetClient {

  private static final Logger log = LoggerFactory.getLogger(DefaultNetClient.class);

  private final VertxInternal vertx;
  private final NetClientOptions options;
  private final DefaultContext actualCtx;
  private final SSLHelper sslHelper;
  private final Map<Channel, DefaultNetSocket> socketMap = new ConcurrentHashMap<>();
  private final Closeable closeHook;
  private Bootstrap bootstrap;

  public DefaultNetClient(VertxInternal vertx, NetClientOptions options) {
    this.vertx = vertx;
    this.options = new NetClientOptions(options);
    this.sslHelper = new SSLHelper(options);
    actualCtx = vertx.getOrCreateContext();
    this.closeHook = doneHandler -> {
      DefaultNetClient.this.close();
      doneHandler.handle(new DefaultFutureResult<>((Void)null));
    };
    actualCtx.addCloseHook(closeHook);
  }

  @Override
  public NetClient connect(int port, String host, final Handler<AsyncResult<NetSocket>> connectHandler) {
    connect(port, host, connectHandler, options.getReconnectAttempts());
    return this;
  }

  @Override
  public NetClient connect(int port, final Handler<AsyncResult<NetSocket>> connectCallback) {
    connect(port, "localhost", connectCallback);
    return this;
  }

  @Override
  public void close() {
    for (NetSocket sock : socketMap.values()) {
      sock.close();
    }
    actualCtx.removeCloseHook(closeHook);
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
    if (bootstrap == null) {
      sslHelper.checkSSL(vertx);

      bootstrap = new Bootstrap();
      bootstrap.group(actualCtx.getEventLoop());
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
          pipeline.addLast("handler", new VertxNetHandler(vertx, socketMap));
        }
      });
    }
    applyConnectionOptions(bootstrap);
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        final Channel ch = channelFuture.channel();

        if (channelFuture.isSuccess()) {

          if (sslHelper.isSSL()) {
            // TCP connected, so now we must do the SSL handshake

            SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

            Future<Channel> fut = sslHandler.handshakeFuture();
            fut.addListener(new GenericFutureListener<Future<Channel>>() {
              @Override
              public void operationComplete(Future<Channel> future) throws Exception {
                if (future.isSuccess()) {
                  connected(ch, connectHandler);
                } else {
                  failed(ch, future.cause(), connectHandler);
                }
              }
            });
          } else {
            connected(ch, connectHandler);
          }
        } else {
          if (remainingAttempts > 0 || remainingAttempts == -1) {
            actualCtx.execute(ch.eventLoop(), () -> {
              log.debug("Failed to create connection. Will retry in " + options.getReconnectInterval() + " milliseconds");
              //Set a timer to retry connection
              vertx.setTimer(options.getReconnectInterval(), new Handler<Long>() {
                public void handle(Long timerID) {
                  connect(port, host, connectHandler, remainingAttempts == -1 ? remainingAttempts : remainingAttempts
                      - 1);
                }
              });
            });
          } else {
            failed(ch, channelFuture.cause(), connectHandler);
          }
        }
      }
    });
  }

  private void connected(final Channel ch, final Handler<AsyncResult<NetSocket>> connectHandler) {
    actualCtx.execute(ch.eventLoop(), () ->  doConnected(ch, connectHandler));
  }

  private void doConnected(Channel ch, final Handler<AsyncResult<NetSocket>> connectHandler) {
    DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, actualCtx, sslHelper, true);
    socketMap.put(ch, sock);
    connectHandler.handle(new DefaultFutureResult<NetSocket>(sock));
  }

  private void failed(Channel ch, final Throwable t, final Handler<AsyncResult<NetSocket>> connectHandler) {
    ch.close();
    actualCtx.execute(ch.eventLoop(), () -> doFailed(connectHandler, t));
  }

  private static void doFailed(Handler<AsyncResult<NetSocket>> connectHandler, Throwable t) {
    connectHandler.handle(new DefaultFutureResult<>(t));
  }
}

