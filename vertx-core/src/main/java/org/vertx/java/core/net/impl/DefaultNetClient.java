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
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.FutureResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.*;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
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
  private final Context actualCtx;
  private final EventLoopContext eventLoopContext;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private Bootstrap bootstrap;
  private final Map<Channel, DefaultNetSocket> socketMap = new ConcurrentHashMap<>();
  private int reconnectAttempts;
  private long reconnectInterval = 1000;
  private boolean configurable = true;

  public DefaultNetClient(VertxInternal vertx) {
    this.vertx = vertx;
    // This is kind of fiddly - this class might be used by a worker, in which case the context is not
    // an event loop context - but we need an event loop context so that netty can deliver any messages for the connection
    // Therefore, if the current context is not an event loop one, we need to create one and register that with the
    // handler manager when registering handlers
    // We then do a check when messages are delivered that we're on the right worker before delivering the message
    // All of this will be massively simplified in Netty 4.0 when the event loop becomes a first class citizen
    actualCtx = vertx.getOrAssignContext();
    actualCtx.putCloseHook(this, new Runnable() {
      public void run() {
        close();
      }
    });
    if (actualCtx instanceof EventLoopContext) {
      eventLoopContext = (EventLoopContext)actualCtx;
    } else {
      eventLoopContext = vertx.createEventLoopContext();
    }
  }

  @Override
  public NetClient connect(int port, String host, AsyncResultHandler<NetSocket> connectHandler) {
    connect(port, host, connectHandler, reconnectAttempts);
    return this;
  }

  @Override
  public NetClient connect(int port, AsyncResultHandler<NetSocket> connectCallback) {
    connect(port, "localhost", connectCallback);
    return this;
  }

  @Override
  public void close() {
    for (NetSocket sock : socketMap.values()) {
      sock.close();
    }
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

  private void connect(final int port, final String host, final AsyncResultHandler<NetSocket> connectHandler,
                       final int remainingAttempts) {
    if (bootstrap == null) {
      // Share the event loop thread to also serve the NetClient's network traffic.
      VertxEventLoopGroup pool = new VertxEventLoopGroup();
      pool.addWorker(eventLoopContext.getWorker());

      tcpHelper.checkSSL(vertx);

      bootstrap = new Bootstrap();
      bootstrap.group(pool);
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          ChannelPipeline pipeline = ch.pipeline();
          pipeline.addLast("exceptionDispatcher", EXCEPTION_DISPATCH_HANDLER);
          pipeline.addLast("flowControl", new FlowControlHandler());

          if (tcpHelper.isSSL()) {
            SSLEngine engine = tcpHelper.getSSLContext().createSSLEngine();
            engine.setUseClientMode(true); //We are on the client side of the connection
            pipeline.addLast("ssl", new SslHandler(engine));
          }
          pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());  // For large file / sendfile support
          pipeline.addLast("handler", new ClientHandler());
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

            ChannelFuture fut = sslHandler.handshake();
            fut.addListener(new ChannelFutureListener() {

              public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess()) {
                  connected(ch, connectHandler);
                } else {
                  failed(ch, channelFuture.cause(), connectHandler);
                }
              }
            });
          } else {
            connected(ch, connectHandler);
          }
        } else {
          if (remainingAttempts > 0 || remainingAttempts == -1) {
            if (actualCtx.isOnCorrectWorker(ch.eventLoop())) {
              vertx.setContext(actualCtx);
              log.debug("Failed to create connection. Will retry in " + reconnectInterval + " milliseconds");
              //Set a timer to retry connection
              vertx.setTimer(reconnectInterval, new Handler<Long>() {
                public void handle(Long timerID) {
                  connect(port, host, connectHandler, remainingAttempts == -1 ? remainingAttempts : remainingAttempts
                                - 1);
                }
              });
            } else {
              actualCtx.execute(new Runnable() {
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
            }
          } else {
            failed(ch, channelFuture.cause(), connectHandler);
          }
        }
      }
    });
  }

  private void connected(final Channel ch, final AsyncResultHandler<NetSocket> connectHandler) {
    if (actualCtx.isOnCorrectWorker(ch.eventLoop())) {
      vertx.setContext(actualCtx);
      doConnected(ch, connectHandler);
    } else {
      actualCtx.execute(new Runnable() {
        public void run() {
          doConnected(ch, connectHandler);
        }
      });
    }
  }

  private void doConnected(Channel ch, AsyncResultHandler<NetSocket> connectHandler) {
    DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, actualCtx);
    socketMap.put(ch, sock);
    connectHandler.handle(new FutureResult<NetSocket>(sock));
  }

  private void failed(Channel ch, final Throwable t, final AsyncResultHandler<NetSocket> connectHandler) {
    ch.close();
    if (actualCtx.isOnCorrectWorker(ch.eventLoop())) {
      vertx.setContext(actualCtx);
      doFailed(connectHandler, t);
    } else {
      actualCtx.execute(new Runnable() {
        public void run() {
          doFailed(connectHandler, t);
        }
      });
    }
  }

  private void doFailed(AsyncResultHandler<NetSocket> connectHandler, Throwable t) {
    connectHandler.handle(new FutureResult<NetSocket>(t));
  }

  private class ClientHandler extends VertxNetHandler {
    public ClientHandler() {
      super(DefaultNetClient.this.vertx, socketMap);
    }

    @Override
    protected Context getContext(DefaultNetSocket connection) {
      return actualCtx;
    }
  }
}

