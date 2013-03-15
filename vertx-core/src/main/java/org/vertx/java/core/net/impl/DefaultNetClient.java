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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.EventLoopContext;
import org.vertx.java.core.impl.ExceptionDispatchHandler;
import org.vertx.java.core.impl.FlowControlHandler;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLContext;
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
  private Handler<Exception> exceptionHandler;
  private int reconnectAttempts;
  private long reconnectInterval = 1000;

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

  public NetClient connect(int port, String host, final Handler<NetSocket> connectHandler) {
    connect(port, host, connectHandler, reconnectAttempts);
    return this;
  }

  public NetClient connect(int port, Handler<NetSocket> connectCallback) {
    connect(port, "localhost", connectCallback);
    return this;
  }

  public void close() {
    for (NetSocket sock : socketMap.values()) {
      sock.close();
    }
  }

  public NetClient setReconnectAttempts(int attempts) {
    if (attempts < -1) {
      throw new IllegalArgumentException("reconnect attempts must be >= -1");
    }
    this.reconnectAttempts = attempts;
    return this;
  }

  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  public NetClient setReconnectInterval(long interval) {
    if (interval < 1) {
      throw new IllegalArgumentException("reconnect interval nust be >= 1");
    }
    this.reconnectInterval = interval;
    return this;
  }

  public long getReconnectInterval() {
    return reconnectInterval;
  }

  public void exceptionHandler(Handler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  public Boolean isTCPNoDelay() {
    return tcpHelper.isTCPNoDelay();
  }

  public Integer getSendBufferSize() {
    return tcpHelper.getSendBufferSize();
  }

  public Integer getReceiveBufferSize() {
    return tcpHelper.getReceiveBufferSize();
  }

  public Boolean isTCPKeepAlive() {
    return tcpHelper.isTCPKeepAlive();
  }

  public Boolean isReuseAddress() {
    return tcpHelper.isReuseAddress();
  }

  public Integer getSoLinger() {
    return tcpHelper.getSoLinger();
  }

  public Integer getTrafficClass() {
    return tcpHelper.getTrafficClass();
  }

  public Long getConnectTimeout() {
    return tcpHelper.getConnectTimeout();
  }

  public NetClient setTCPNoDelay(boolean tcpNoDelay) {
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  public NetClient setSendBufferSize(int size) {
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  public NetClient setReceiveBufferSize(int size) {
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  public NetClient setTCPKeepAlive(boolean keepAlive) {
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  public NetClient setReuseAddress(boolean reuse) {
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  public NetClient setSoLinger(int linger) {
    if (linger < 0) {
      tcpHelper.setSoLinger(null);
    } else {
      tcpHelper.setSoLinger(linger);
    }
    return this;
  }

  public NetClient setTrafficClass(int trafficClass) {
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  public NetClient setConnectTimeout(long timeout) {
    tcpHelper.setConnectTimeout(timeout);
    return this;
  }

  public boolean isSSL() {
    return tcpHelper.isSSL();
  }

  public String getKeyStorePath() {
    return tcpHelper.getKeyStorePath();
  }

  public String getKeyStorePassword() {
    return tcpHelper.getKeyStorePassword();
  }

  public String getTrustStorePath() {
    return tcpHelper.getTrustStorePath();
  }

  public String getTrustStorePassword() {
    return tcpHelper.getTrustStorePassword();
  }

  public TCPSSLHelper.ClientAuth getClientAuth() {
    return tcpHelper.getClientAuth();
  }

  public SSLContext getSSLContext() {
    return tcpHelper.getSSLContext();
  }

  public boolean isTrustAll() {
    return tcpHelper.isTrustAll();
  }

  public NetClient setSSL(boolean ssl) {
    tcpHelper.setSSL(ssl);
    return this;
  }

  public NetClient setKeyStorePath(String path) {
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  public NetClient setKeyStorePassword(String pwd) {
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  public NetClient setTrustStorePath(String path) {
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  public NetClient setTrustStorePassword(String pwd) {
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  public NetClient setTrustAll(boolean trustAll) {
    tcpHelper.setTrustAll(trustAll);
    return this;
  }

  private void connect(final int port, final String host, final Handler<NetSocket> connectHandler,
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
    }
    // TODO: FIx me
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
                  failed(ch, new SSLHandshakeException("Failed to create SSL connection"));
                }
              }
            });
          } else {
            connected(ch, connectHandler);
          }
        } else {
          if (remainingAttempts > 0 || remainingAttempts == -1) {
            if (actualCtx.isOnCorrectWorker(ch.eventLoop())) {
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
            failed(ch, channelFuture.cause());
          }
        }
      }
    });
  }

  private void connected(final Channel ch, final Handler<NetSocket> connectHandler) {
    if (actualCtx.isOnCorrectWorker(ch.eventLoop())) {
      DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, actualCtx);
      socketMap.put(ch, sock);
      connectHandler.handle(sock);
    } else {
      actualCtx.execute(new Runnable() {
        public void run() {
          DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, actualCtx);
          socketMap.put(ch, sock);
          connectHandler.handle(sock);
        }
      });
    }

  }

  private void failed(Channel ch, final Throwable t) {
    ch.close();
    if (t instanceof Exception && exceptionHandler != null) {
      if (actualCtx.isOnCorrectWorker(ch.eventLoop())) {
        exceptionHandler.handle((Exception) t);
      } else {
        actualCtx.execute(new Runnable() {
          public void run() {
            exceptionHandler.handle((Exception) t);
          }
        });
      }
    } else {
      log.error("Unhandled exception", t);
    }
  }

  private class ClientHandler extends VertxNetHandler {
    public ClientHandler() {
      super(socketMap);
    }

    @Override
    protected Context getContext(DefaultNetSocket connection) {
      // TODO: Why ?
      return actualCtx;
    }
  }
}

