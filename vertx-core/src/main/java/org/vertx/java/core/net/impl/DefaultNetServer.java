
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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.impl.Closeable;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultNetServer implements NetServer, Closeable {

  private static final Logger log = LoggerFactory.getLogger(DefaultNetServer.class);

  private final VertxInternal vertx;
  private final DefaultContext actualCtx;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private final Map<Channel, DefaultNetSocket> socketMap = new ConcurrentHashMap<Channel, DefaultNetSocket>();
  private Handler<NetSocket> connectHandler;

  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private volatile ServerID id;
  private DefaultNetServer actualServer;
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<NetSocket> handlerManager = new HandlerManager<>(availableWorkers);
  private String host;
  private volatile int port;
  private ChannelFuture bindFuture;

  public DefaultNetServer(VertxInternal vertx) {
    this.vertx = vertx;
    actualCtx = vertx.getOrCreateContext();
    actualCtx.addCloseHook(this);
    tcpHelper.setReuseAddress(true);
  }

  @Override
  public NetServer connectHandler(Handler<NetSocket> connectHandler) {
    this.connectHandler = connectHandler;
    return this;
  }

  public NetServer listen(int port) {
    listen(port, "0.0.0.0", null);
    return this;
  }

  public NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler) {
    listen(port, "0.0.0.0", listenHandler);
    return this;
  }

  public NetServer listen(int port, String host) {
    listen(port, host, null);
    return this;
  }

  public NetServer listen(final int port, final String host, final Handler<AsyncResult<NetServer>> listenHandler) {
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
      if (shared == null || port == 0) { // Wildcard port will imply a new actual server each time
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(availableWorkers);
        bootstrap.channel(NioServerSocketChannel.class);
        tcpHelper.checkSSL(vertx);

        bootstrap.childHandler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            if (tcpHelper.isSSL()) {
              SslHandler sslHandler = tcpHelper.createSslHandler(vertx, false);
              pipeline.addLast("ssl", sslHandler);
            }
            if (tcpHelper.isSSL()) {
              // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
              pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
            }
            pipeline.addLast("handler", new ServerHandler());
            }
        });

        tcpHelper.applyConnectionOptions(bootstrap);

        if (connectHandler != null) {
          // Share the event loop thread to also serve the NetServer's network traffic.
          handlerManager.addHandler(connectHandler, actualCtx);
        }

        try {
          InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName(host), port);
          bindFuture = bootstrap.bind(addr).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
              runListeners();
            }
          });
          this.addListener(new Runnable() {
            @Override
            public void run() {
              if (bindFuture.isSuccess()) {
                log.trace("Net server listening on " + host + ":" + bindFuture.channel().localAddress());
                // Update port to actual port - wildcard port 0 might have been used
                DefaultNetServer.this.port = ((InetSocketAddress)bindFuture.channel().localAddress()).getPort();
                DefaultNetServer.this.id = new ServerID(DefaultNetServer.this.port, id.host);
                vertx.sharedNetServers().put(id, DefaultNetServer.this);
              } else {
                vertx.sharedNetServers().remove(id);
              }
            }
          });
          serverChannelGroup.add(bindFuture.channel());
        } catch (final Throwable t) {
          // Make sure we send the exception back through the handler (if any)
          if (listenHandler != null) {
            vertx.runOnContext(new VoidHandler() {
              @Override
              protected void handle() {
                listenHandler.handle(new DefaultFutureResult<NetServer>(t));
              }
            });
          } else {
            // No handler - log so user can see failure
            actualCtx.reportException(t);
          }
          listening = false;
          return this;
        }
        if (port != 0) {
          vertx.sharedNetServers().put(id, this);
        }
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        checkConfigs(actualServer, this);
        actualServer = shared;
        this.port = shared.port();
        if (connectHandler != null) {
          // Share the event loop thread to also serve the NetServer's network traffic.
          actualServer.handlerManager.addHandler(connectHandler, actualCtx);
        }
      }

      // just add it to the future so it gets notified once the bind is complete
      actualServer.addListener(new Runnable() {
        public void run() {
          if (listenHandler != null) {
            final AsyncResult<NetServer> res;
            if (actualServer.bindFuture.isSuccess()) {
              res = new DefaultFutureResult<NetServer>(DefaultNetServer.this);
            } else {
              listening = false;
              res = new DefaultFutureResult<>(actualServer.bindFuture.cause());
            }
            actualCtx.execute(actualServer.bindFuture.channel().eventLoop(), new Runnable() {
              @Override
              public void run() {
                listenHandler.handle(res);
              }
            });
          } else if (!actualServer.bindFuture.isSuccess()) {
            // No handler - log so user can see failure
            actualCtx.reportException(actualServer.bindFuture.cause());
            listening = false;
          }
        }
      });

    }
    return this;
  }

  private Queue<Runnable> bindListeners = new ConcurrentLinkedQueue<>();

  private boolean listenersRun;

  private synchronized void addListener(Runnable runner) {
    if (!listenersRun) {
      bindListeners.add(runner);
    } else {
      // Run it now
      runner.run();
    }
  }

  private synchronized void runListeners() {
    Runnable runner;
    while ((runner = bindListeners.poll()) != null) {
      runner.run();
    }
    listenersRun = true;
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
        actualServer.handlerManager.removeHandler(connectHandler, actualCtx);

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
    actualCtx.removeCloseHook(this);
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
  public NetServer setSSLContext(SSLContext sslContext) {
    checkListening();
    tcpHelper.setExternalSSLContext(sslContext);
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

  private void actualClose(final DefaultContext closeContext, final Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedNetServers().remove(id);
    }

    for (DefaultNetSocket sock : socketMap.values()) {
      sock.close();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    vertx.setContext(closeContext);

    ChannelGroupFuture fut = serverChannelGroup.close();
    fut.addListener(new ChannelGroupFutureListener() {
      public void operationComplete(ChannelGroupFuture fut) throws Exception {
        executeCloseDone(closeContext, done, fut.cause());
      }
    });

  }

  private void checkConfigs(DefaultNetServer currentServer, DefaultNetServer newServer) {
    //TODO check configs are the same
  }

  private void executeCloseDone(final DefaultContext closeContext, final Handler<AsyncResult<Void>> done, final Exception e) {
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

        Future<Channel> fut = sslHandler.handshakeFuture();
        fut.addListener(new GenericFutureListener<Future<Channel>>() {
          @Override
          public void operationComplete(Future<Channel> future) throws Exception {
            if (future.isSuccess()) {
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
      handler.context.execute(ch.eventLoop(), new Runnable() {
        public void run() {
          doConnected(ch, handler);
        }
      });
    }

    private void doConnected(Channel ch, HandlerHolder<NetSocket> handler) {
      DefaultNetSocket sock = new DefaultNetSocket(vertx, ch, handler.context, tcpHelper, false);
      socketMap.put(ch, sock);
      handler.handler.handle(sock);
    }
  }
}
