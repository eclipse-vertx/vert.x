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

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.EventLoopContext;
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

  private final VertxInternal vertx;
  private final Context actualCtx;
  private final EventLoopContext eventLoopContext;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private ClientBootstrap bootstrap;
  private Map<Channel, DefaultNetSocket> socketMap = new ConcurrentHashMap<>();
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

  public Boolean isSoLinger() {
    return tcpHelper.isSoLinger();
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

  public NetClient setSoLinger(boolean linger) {
    tcpHelper.setSoLinger(linger);
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
      VertxWorkerPool pool = new VertxWorkerPool();
      pool.addWorker(eventLoopContext.getWorker());

      NioClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory(
          vertx.getClientAcceptorPool(), pool);
      bootstrap = new ClientBootstrap(channelFactory);

      tcpHelper.checkSSL(vertx);

      bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        public ChannelPipeline getPipeline() throws Exception {
          ChannelPipeline pipeline = Channels.pipeline();
          if (tcpHelper.isSSL()) {
            SSLEngine engine = tcpHelper.getSSLContext().createSSLEngine();
            engine.setUseClientMode(true); //We are on the client side of the connection
            pipeline.addLast("ssl", new SslHandler(engine));
          }
          pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());  // For large file / sendfile support
          pipeline.addLast("handler", new ClientHandler());
          return pipeline;
        }
      });
    }
    bootstrap.setOptions(tcpHelper.generateConnectionOptions(false));
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();

        if (channelFuture.isSuccess()) {

          if (tcpHelper.isSSL()) {
            // TCP connected, so now we must do the SSL handshake

            SslHandler sslHandler = (SslHandler)ch.getPipeline().get("ssl");

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
          } else {
            failed(ch, channelFuture.getCause());
          }
        }
      }
    });
  }

  private void connected(final NioSocketChannel ch, final Handler<NetSocket> connectHandler) {
    actualCtx.execute(new Runnable() {
      public void run() {
        DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, actualCtx);
        socketMap.put(ch, sock);
        connectHandler.handle(sock);
      }
    });
  }

  private void failed(NioSocketChannel ch, final Throwable t) {
  	ch.close();
    if (t instanceof Exception && exceptionHandler != null) {
      actualCtx.execute(new Runnable() {
        public void run() {
          exceptionHandler.handle((Exception) t);
        }
      });
    } else {
      log.error("Unhandled exception", t);
    }
  }

  private class ClientHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
    }

    @Override
    public void channelClosed(ChannelHandlerContext chctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final DefaultNetSocket sock = socketMap.remove(ch);
      if (sock != null) {
        sock.getContext().execute(new Runnable() {
          public void run() {
            sock.handleClosed();
          }
        });
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext chctx, MessageEvent e) {
      final DefaultNetSocket sock = socketMap.get(chctx.getChannel());
      if (sock != null) {
        final ChannelBuffer cb = (ChannelBuffer) e.getMessage();
        NioSocketChannel ch = (NioSocketChannel) e.getChannel();
        // We need to do this since it's possible the server is being used from a worker context
        if (actualCtx.isOnCorrectWorker(ch.getWorker())) {
          sock.handleDataReceived(new Buffer(cb));
        } else {
          actualCtx.execute(new Runnable() {
            public void run() {
              sock.handleDataReceived(new Buffer(cb));
            }
          });
        }
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext chctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final DefaultNetSocket sock = socketMap.get(ch);
      ChannelState state = e.getState();
      if (state == ChannelState.INTEREST_OPS) {
        actualCtx.execute(new Runnable() {
          public void run() {
            sock.handleInterestedOpsChanged();
          }
        });
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext chctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.remove(ch);
      final Throwable t = e.getCause();
      if (sock != null && t instanceof Exception) {
        actualCtx.execute(new Runnable() {
          public void run() {
            sock.handleException((Exception) t);
            ch.close();
          }
        });
      } else {
        // Ignore - any exceptions before a channel exists will be passed manually via the failed(...) method
      }
    }
  }

}

