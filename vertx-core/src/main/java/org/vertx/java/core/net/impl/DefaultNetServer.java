
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLContext;
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
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private ServerID id;
  private DefaultNetServer actualServer;
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<NetSocket> handlerManager = new HandlerManager<>(availableWorkers);

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

  public NetServer connectHandler(Handler<NetSocket> connectHandler) {
    this.connectHandler = connectHandler;
    return this;
  }

  public NetServer listen(int port) {
    listen(port, "0.0.0.0");
    return this;
  }

  public NetServer listen(int port, String host) {
    if (connectHandler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;

    synchronized (vertx.sharedNetServers()) {
      id = new ServerID(port, host);
      DefaultNetServer shared = vertx.sharedNetServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels");

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(vertx.getServerAcceptorPool(), availableWorkers);
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

        try {
          //TODO - currently bootstrap.bind is blocking - need to make it non blocking by not using bootstrap directly
          Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port)).syncUninterruptibly().channel();
          serverChannelGroup.add(serverChannel);
          log.trace("Net server listening on " + host + ":" + port);
        } catch (ChannelException | UnknownHostException e) {
          throw new IllegalArgumentException(e.getMessage());
        }
        vertx.sharedNetServers().put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        checkConfigs(actualServer, this);
        actualServer = shared;
      }
      // Share the event loop thread to also serve the NetServer's network traffic.
      actualServer.handlerManager.addHandler(connectHandler, eventLoopContext);
    }
    return this;
  }

  public void close() {
    close(null);
  }

  public void close(final Handler<Void> done) {
    if (!listening) {
      if (done != null) {
        executeCloseDone(actualCtx, done);
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
            executeCloseDone(actualCtx, done);
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

  private void actualClose(final Context closeContext, final Handler<Void> done) {
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

    executeCloseDone(closeContext, done);
  }

  private void checkConfigs(DefaultNetServer currentServer, DefaultNetServer newServer) {
    //TODO check configs are the same
  }

  private void executeCloseDone(final Context closeContext, final Handler<Void> done) {
    if (done != null) {
      closeContext.execute(new Runnable() {
      public void run() {
        done.handle(null);
      }
    });
    }
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

  public Integer getAcceptBacklog() {
    return tcpHelper.getAcceptBacklog();
  }

  public NetServer setTCPNoDelay(boolean tcpNoDelay) {
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  public NetServer setSendBufferSize(int size) {
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  public NetServer setReceiveBufferSize(int size) {
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  public NetServer setTCPKeepAlive(boolean keepAlive) {
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  public NetServer setReuseAddress(boolean reuse) {
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  public NetServer setSoLinger(int linger) {
    if (linger < 0) {
      tcpHelper.setSoLinger(null);
    } else {
      tcpHelper.setSoLinger(linger);
    }
    return this;
  }

  public NetServer setTrafficClass(int trafficClass) {
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  public NetServer setAcceptBacklog(int backlog) {
    tcpHelper.setAcceptBacklog(backlog);
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

  public NetServer setSSL(boolean ssl) {
    tcpHelper.setSSL(ssl);
    return this;
  }

  public NetServer setKeyStorePath(String path) {
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  public NetServer setKeyStorePassword(String pwd) {
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  public NetServer setTrustStorePath(String path) {
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  public NetServer setTrustStorePassword(String pwd) {
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  public NetServer setClientAuthRequired(boolean required) {
    tcpHelper.setClientAuthRequired(required);
    return this;
  }

  private class ServerHandler extends VertxNetHandler {
    public ServerHandler() {
      super(socketMap);
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
        vertx.setContext(handler.context);
        DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, handler.context);
        socketMap.put(ch, sock);
        handler.handler.handle(sock);
      } else {
        handler.context.execute(new Runnable() {
          public void run() {
            DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, handler.context);
            socketMap.put(ch, sock);
            handler.handler.handle(sock);
          }
        });
      }
    }
  }
}
