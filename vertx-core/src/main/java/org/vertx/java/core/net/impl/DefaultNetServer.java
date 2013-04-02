
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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.*;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLEngine;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultNetServer implements NetServer {

  private static final Logger log = LoggerFactory.getLogger(DefaultNetServer.class);
  private static final ExceptionDispatchHandler EXCEPTION_DISPATCH_HANDLER = new ExceptionDispatchHandler();

  private final VertxInternal vertx;
  private final Context actualCtx;
  private final EventLoopContext eventLoopContext;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private final Map<Channel, DefaultNetSocket> socketMap = new ConcurrentHashMap<Channel, DefaultNetSocket>();
  private Handler<NetSocket> connectHandler;
  private Handler<Exception> exceptionHandler;

  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private ServerID id;
  private DefaultNetServer actualServer;
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<NetSocket> handlerManager = new HandlerManager<>(availableWorkers);
  private String host;
  private int port;

  private ChannelFuture bindFuture;

  public DefaultNetServer(VertxInternal vertx) {
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
    tcpHelper.setReuseAddress(true);
  }

  @Override
  public NetServer connectHandler(Handler<NetSocket> connectHandler) {
    this.connectHandler = connectHandler;
    return this;
  }

  public void listen(int port) {
    listen(port, "0.0.0.0", null);
  }

  public void listen(int port, Handler<NetServer> listenHandler) {
    listen(port, "0.0.0.0", listenHandler);
  }

  public void listen(int port, String host) {
    listen(port, host, null);
  }

  public void listen(final int port, final String host, final Handler<NetServer> listenHandler) {
    if (connectHandler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;
    this.host = host;

    synchronized (vertx.sharedNetServers()) {
      id = new ServerID(port, host);
      DefaultNetServer shared = vertx.sharedNetServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels");

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(availableWorkers);
        bootstrap.channel(NioServerSocketChannel.class);
        tcpHelper.checkSSL(vertx);

        bootstrap.childHandler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("exceptionDispatcher", EXCEPTION_DISPATCH_HANDLER);
            pipeline.addLast("flowControl", new FlowControlHandler());
            if (tcpHelper.isSSL()) {
              SSLEngine engine = tcpHelper.getSSLContext().createSSLEngine();
              engine.setUseClientMode(false);
              switch (tcpHelper.getClientAuth()) {
                case REQUEST: {
                  engine.setWantClientAuth(true);
                  break;
                }
                case REQUIRED: {
                  engine.setNeedClientAuth(true);
                  break;
                }
                case NONE: {
                  engine.setNeedClientAuth(false);
                  break;
                }
              }
              pipeline.addLast("ssl", new SslHandler(engine));
            }
            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());  // For large file / sendfile support
            pipeline.addLast("handler", new ServerHandler());
            }
        });

        tcpHelper.applyConnectionOptions(bootstrap);

        if (connectHandler != null) {
          // Share the event loop thread to also serve the NetServer's network traffic.
          handlerManager.addHandler(connectHandler, eventLoopContext);
        }

        try {

          bindFuture = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port)).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
              if (future.isSuccess()) {
                log.trace("Net server listening on " + host + ":" + port);
              }
            }
          });
          serverChannelGroup.add(bindFuture.channel());
        } catch (ChannelException | UnknownHostException e) {
          throw new IllegalArgumentException(e.getMessage());
        }
        vertx.sharedNetServers().put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        checkConfigs(actualServer, this);

        actualServer = shared;

        if (connectHandler != null) {
          // Share the event loop thread to also serve the NetServer's network traffic.
          actualServer.handlerManager.addHandler(connectHandler, eventLoopContext);
        }
      }

      // just add it to the future so it gets notified once the bind is complete
      actualServer.bindFuture.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            if (listenHandler != null) {
              if (eventLoopContext.isOnCorrectWorker(future.channel().eventLoop())) {
                vertx.setContext(eventLoopContext);
                listenHandler.handle(DefaultNetServer.this);
              } else {
                eventLoopContext.execute(new Runnable() {
                  @Override
                  public void run() {
                    listenHandler.handle(DefaultNetServer.this);
                  }
                });
              }
            }
          } else {
              Handler<Exception> exceptionHandler = exceptionHandler();
              if (exceptionHandler != null) {
                  exceptionHandler.handle((Exception) future.cause());
              }
            close();
          }
        }
      });

    }
  }

  @Override
  public NetServer exceptionHandler(Handler<Exception> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  @Override
  public Handler<Exception> exceptionHandler() {
    return exceptionHandler;
  }

  public void close() {
    close(null);
  }

  @Override
  public void close(final Handler<AsyncResult<Void>> done) {
    if (!listening) {
      if (done != null) {
        executeCloseDone(actualCtx, done, null);
      }
      return;
    }
    listening = false;
    synchronized (vertx.sharedNetServers()) {

      if (actualServer != null) {
        actualServer.handlerManager.removeHandler(connectHandler, eventLoopContext);

        if (actualServer.handlerManager.hasHandlers()) {
          // The actual server still has handlers so we don't actually close it
          if (done != null) {
            executeCloseDone(actualCtx, done, null);
          }
        } else {
          // No Handlers left so close the actual server
          // The done handler needs to be executed on the context that calls close, NOT the context
          // of the actual server
          actualServer.actualClose(actualCtx, done);
        }
      }
    }
  }

  @Override
  public String host() {
    return host;
  }

  @Override
  public int port() {
    return port;
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
  public int getAcceptBacklog() {
    return tcpHelper.getAcceptBacklog();
  }

  @Override
  public NetServer setTCPNoDelay(boolean tcpNoDelay) {
    checkListening();
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public NetServer setSendBufferSize(int size) {
    checkListening();
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  @Override
  public NetServer setReceiveBufferSize(int size) {
    checkListening();
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  @Override
  public NetServer setTCPKeepAlive(boolean keepAlive) {
    checkListening();
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  @Override
  public NetServer setReuseAddress(boolean reuse) {
    checkListening();
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  @Override
  public NetServer setSoLinger(int linger) {
    checkListening();
    tcpHelper.setSoLinger(linger);
    return this;
  }

  @Override
  public NetServer setTrafficClass(int trafficClass) {
    checkListening();
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public NetServer setAcceptBacklog(int backlog) {
    checkListening();
    tcpHelper.setAcceptBacklog(backlog);
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
  public boolean isClientAuthRequired() {
    return tcpHelper.getClientAuth() == TCPSSLHelper.ClientAuth.REQUIRED;
  }

  @Override
  public NetServer setSSL(boolean ssl) {
    checkListening();
    tcpHelper.setSSL(ssl);
    return this;
  }

  @Override
  public NetServer setKeyStorePath(String path) {
    checkListening();
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  @Override
  public NetServer setKeyStorePassword(String pwd) {
    checkListening();
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  @Override
  public NetServer setTrustStorePath(String path) {
    checkListening();
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  @Override
  public NetServer setTrustStorePassword(String pwd) {
    checkListening();
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  @Override
  public NetServer setClientAuthRequired(boolean required) {
    checkListening();
    tcpHelper.setClientAuthRequired(required);
    return this;
  }

  @Override
  public NetServer setUsePooledBuffers(boolean pooledBuffers) {
    checkListening();
    tcpHelper.setUsePooledBuffers(pooledBuffers);
    return this;
  }

  @Override
  public boolean isUsePooledBuffers() {
    return tcpHelper.isUsePooledBuffers();
  }

  private void actualClose(final Context closeContext, final Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedNetServers().remove(id);
    }

    for (DefaultNetSocket sock : socketMap.values()) {
      sock.internalClose();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    vertx.setContext(closeContext);

    final CountDownLatch latch = new CountDownLatch(1);

    ChannelGroupFuture fut = serverChannelGroup.close();
    fut.addListener(new ChannelGroupFutureListener() {
      public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
        latch.countDown();
      }
    });

    // Always sync
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
    }

    executeCloseDone(closeContext, done, fut.cause());
  }

  private void checkConfigs(DefaultNetServer currentServer, DefaultNetServer newServer) {
    //TODO check configs are the same
  }

  private void executeCloseDone(final Context closeContext, final Handler<AsyncResult<Void>> done, final Exception e) {
    if (done != null) {
      closeContext.execute(new Runnable() {
        public void run() {
          done.handle(new DefaultFutureResult<Void>(e));
        }
      });
    }
  }

  private void checkListening() {
    if (listening) {
      throw new IllegalStateException("Can't set property when server is listening");
    }
  }

  private class ServerHandler extends VertxNetHandler {
    public ServerHandler() {
      super(DefaultNetServer.this.vertx, socketMap);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      final Channel ch = ctx.channel();
      EventLoop worker = ch.eventLoop();

      //Choose a handler
      final HandlerHolder<NetSocket> handler = handlerManager.chooseHandler(worker);
      if (handler == null) {
        //Ignore
        return;
      }

      if (tcpHelper.isSSL()) {
        SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

        ChannelFuture fut = sslHandler.handshake();
        fut.addListener(new ChannelFutureListener() {

          public void operationComplete(ChannelFuture channelFuture) throws Exception {
            if (channelFuture.isSuccess()) {
              connected(ch, handler);
            } else {
              log.error("Client from origin " + ch.remoteAddress() + " failed to connect over ssl");
            }
          }
        });
      } else {
        connected(ch, handler);
      }
    }

    private void connected(final Channel ch, final HandlerHolder<NetSocket> handler) {
      if (handler.context.isOnCorrectWorker(ch.eventLoop())) {
        try {
          vertx.setContext(handler.context);
          doConnected(ch, handler);
        } catch (Throwable t) {
          handler.context.reportException(t);
        }
      } else {
        handler.context.execute(new Runnable() {
          public void run() {
            doConnected(ch, handler);
          }
        });
      }
    }

    private void doConnected(Channel ch, HandlerHolder<NetSocket> handler) {
      DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, handler.context);
      socketMap.put(ch, sock);
      handler.handler.handle(sock);
    }
  }
}
