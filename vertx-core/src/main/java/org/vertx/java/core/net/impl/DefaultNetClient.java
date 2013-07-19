/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.vertx.java.core.impl.*;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultNetClient implements NetClient {

  private static final Logger log = LoggerFactory.getLogger(DefaultNetClient.class);
  private static final ExceptionDispatchHandler EXCEPTION_DISPATCH_HANDLER = new ExceptionDispatchHandler();

  private final VertxInternal vertx;
  private final DefaultContext actualCtx;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private Bootstrap bootstrap;
  private final Map<Channel, DefaultNetSocket> socketMap = new ConcurrentHashMap<>();
  private int reconnectAttempts;
  private long reconnectInterval = 1000;
  private boolean configurable = true;
  private final Closeable closeHook = new Closeable() {
    @Override
    public void close(Handler<AsyncResult<Void>> doneHandler) {
      DefaultNetClient.this.close();
      doneHandler.handle(new DefaultFutureResult<>((Void)null));
    }
  };

  public DefaultNetClient(VertxInternal vertx) {
    this.vertx = vertx;
    actualCtx = vertx.getOrCreateContext();
    actualCtx.addCloseHook(closeHook);
  }

  @Override
  public NetClient connect(int port, String host, final Handler<AsyncResult<NetSocket>> connectHandler) {
    connect(port, host, connectHandler, reconnectAttempts);
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

  @Override
  public NetClient setReconnectAttempts(int attempts) {
    checkConfigurable();
    if (attempts < -1) {
      throw new IllegalArgumentException("reconnect attempts must be >= -1");
    }
    this.reconnectAttempts = attempts;
    return this;
  }

  @Override
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  @Override
  public NetClient setReconnectInterval(long interval) {
    checkConfigurable();
    if (interval < 1) {
      throw new IllegalArgumentException("reconnect interval nust be >= 1");
    }
    this.reconnectInterval = interval;
    return this;
  }

  @Override
  public long getReconnectInterval() {
    return reconnectInterval;
  }

  @Override
  public boolean isTCPNoDelay() {
    return tcpHelper.isTCPNoDelay();
  }

  @Override
  public int getSendBufferSize() {
    return tcpHelper.getSendBufferSize();
  }

  @Override
  public int getReceiveBufferSize() {
    return tcpHelper.getReceiveBufferSize();
  }

  @Override
  public boolean isTCPKeepAlive() {
    return tcpHelper.isTCPKeepAlive();
  }

  @Override
  public boolean isReuseAddress() {
    return tcpHelper.isReuseAddress();
  }

  @Override
  public int getSoLinger() {
    return tcpHelper.getSoLinger();
  }

  @Override
  public int getTrafficClass() {
    return tcpHelper.getTrafficClass();
  }

  @Override
  public int getConnectTimeout() {
    return tcpHelper.getConnectTimeout();
  }

  @Override
  public NetClient setTCPNoDelay(boolean tcpNoDelay) {
    checkConfigurable();
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public NetClient setSendBufferSize(int size) {
    checkConfigurable();
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  @Override
  public NetClient setReceiveBufferSize(int size) {
    checkConfigurable();
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  @Override
  public NetClient setTCPKeepAlive(boolean keepAlive) {
    checkConfigurable();
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  @Override
  public NetClient setReuseAddress(boolean reuse) {
    checkConfigurable();
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  @Override
  public NetClient setSoLinger(int linger) {
    checkConfigurable();
    tcpHelper.setSoLinger(linger);
    return this;
  }

  @Override
  public NetClient setTrafficClass(int trafficClass) {
    checkConfigurable();
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public NetClient setConnectTimeout(int timeout) {
    checkConfigurable();
    tcpHelper.setConnectTimeout(timeout);
    return this;
  }

  @Override
  public boolean isSSL() {
    return tcpHelper.isSSL();
  }

  @Override
  public String getKeyStorePath() {
    return tcpHelper.getKeyStorePath();
  }

  @Override
  public String getKeyStorePassword() {
    return tcpHelper.getKeyStorePassword();
  }

  @Override
  public String getTrustStorePath() {
    return tcpHelper.getTrustStorePath();
  }

  @Override
  public String getTrustStorePassword() {
    return tcpHelper.getTrustStorePassword();
  }

  @Override
  public boolean isTrustAll() {
    return tcpHelper.isTrustAll();
  }

  @Override
  public NetClient setSSL(boolean ssl) {
    checkConfigurable();
    tcpHelper.setSSL(ssl);
    return this;
  }

  @Override
  public NetClient setKeyStorePath(String path) {
    checkConfigurable();
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  @Override
  public NetClient setKeyStorePassword(String pwd) {
    checkConfigurable();
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  @Override
  public NetClient setTrustStorePath(String path) {
    checkConfigurable();
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  @Override
  public NetClient setTrustStorePassword(String pwd) {
    checkConfigurable();
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  @Override
  public NetClient setTrustAll(boolean trustAll) {
    checkConfigurable();
    tcpHelper.setTrustAll(trustAll);
    return this;
  }

  @Override
  public NetClient setUsePooledBuffers(boolean pooledBuffers) {
    checkConfigurable();
    tcpHelper.setUsePooledBuffers(pooledBuffers);
    return this;
  }

  @Override
  public boolean isUsePooledBuffers() {
    return tcpHelper.isUsePooledBuffers();
  }

  private void checkConfigurable() {
    if (!configurable) {
      throw new IllegalStateException("Can't set property after connect has been called");
    }
  }

  private void connect(final int port, final String host, final Handler<AsyncResult<NetSocket>> connectHandler,
                       final int remainingAttempts) {
    if (bootstrap == null) {
      tcpHelper.checkSSL(vertx);

      bootstrap = new Bootstrap();
      bootstrap.group(actualCtx.getEventLoop());
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          ChannelPipeline pipeline = ch.pipeline();
          pipeline.addLast("exceptionDispatcher", EXCEPTION_DISPATCH_HANDLER);

          if (tcpHelper.isSSL()) {
            SSLEngine engine = tcpHelper.getSSLContext().createSSLEngine();
            engine.setUseClientMode(true); //We are on the client side of the connection
            pipeline.addLast("ssl", new SslHandler(engine));
          }
          if (tcpHelper.isSSL()) {
            // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
          }
          pipeline.addLast("handler", new VertxNetHandler(vertx, socketMap));
        }
      });
      configurable = false;
    }
    tcpHelper.applyConnectionOptions(bootstrap);
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        final Channel ch = channelFuture.channel();

        if (channelFuture.isSuccess()) {

          if (tcpHelper.isSSL()) {
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
            actualCtx.execute(ch.eventLoop(), new Runnable() {
              public void run() {
                log.debug("Failed to create connection. Will retry in " + reconnectInterval + " milliseconds");
                //Set a timer to retry connection
                vertx.setTimer(reconnectInterval, new Handler<Long>() {
                  public void handle(Long timerID) {
                    connect(port, host, connectHandler, remainingAttempts == -1 ? remainingAttempts : remainingAttempts
                        - 1);
                  }
                });
              }
            });
          } else {
            failed(ch, channelFuture.cause(), connectHandler);
          }
        }
      }
    });
  }

  private void connected(final Channel ch, final Handler<AsyncResult<NetSocket>> connectHandler) {
    actualCtx.execute(ch.eventLoop(), new Runnable() {
      public void run() {
        doConnected(ch, connectHandler);
      }
    });
  }

  private void doConnected(Channel ch, final Handler<AsyncResult<NetSocket>> connectHandler) {
    DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, actualCtx);
    socketMap.put(ch, sock);
    connectHandler.handle(new DefaultFutureResult<NetSocket>(sock));
  }

  private void failed(Channel ch, final Throwable t, final Handler<AsyncResult<NetSocket>> connectHandler) {
    ch.close();
    actualCtx.execute(ch.eventLoop(), new Runnable() {
      public void run() {
        doFailed(connectHandler, t);
      }
    });
  }

  private static void doFailed(Handler<AsyncResult<NetSocket>> connectHandler, Throwable t) {
    connectHandler.handle(new DefaultFutureResult<NetSocket>(t));
  }
}

