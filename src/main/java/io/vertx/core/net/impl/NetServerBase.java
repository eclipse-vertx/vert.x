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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class NetServerBase<C extends ConnectionBase> implements Closeable, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(NetServerBase.class);

  protected final VertxInternal vertx;
  protected final NetServerOptions options;
  protected final ContextImpl creatingContext;
  protected final SSLHelper sslHelper;
  protected final boolean logEnabled;
  private final Map<Channel, C> socketMap = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<Handler<? super C>> handlerManager = new HandlerManager<>(availableWorkers);
  private ChannelGroup serverChannelGroup;
  private boolean paused;
  private volatile boolean listening;
  private Handler<? super C> registeredHandler;
  private volatile ServerID id;
  private NetServerBase actualServer;
  private AsyncResolveConnectHelper bindFuture;
  private volatile int actualPort;
  private ContextImpl listenContext;
  private TCPMetrics metrics;

  public NetServerBase(VertxInternal vertx, NetServerOptions options) {
    this.vertx = vertx;
    this.options = new NetServerOptions(options);
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
    this.creatingContext = vertx.getContext();
    this.logEnabled = options.getLogActivity();
    if (creatingContext != null) {
      if (creatingContext.isMultiThreadedWorkerContext()) {
        throw new IllegalStateException("Cannot use NetServer in a multi-threaded worker verticle");
      }
      creatingContext.addCloseHook(this);
    }
  }

  protected synchronized void pauseAccepting() {
    paused = true;
  }

  protected synchronized void resumeAccepting() {
    paused = false;
  }

  protected synchronized boolean isPaused() {
    return paused;
  }

  protected boolean isListening() {
    return listening;
  }

  protected abstract void initChannel(ChannelPipeline pipeline);

  public synchronized void listen(Handler<? super C> handler, int port, String host, Handler<AsyncResult<Void>> listenHandler) {
    if (handler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;

    listenContext = vertx.getOrCreateContext();
    registeredHandler = handler;

    synchronized (vertx.sharedNetServers()) {
      this.actualPort = port; // Will be updated on bind for a wildcard port
      id = new ServerID(port, host);
      NetServerBase shared = vertx.sharedNetServers().get(id);
      if (shared == null || port == 0) { // Wildcard port will imply a new actual server each time
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(availableWorkers);
        bootstrap.channel(NioServerSocketChannel.class);
        sslHelper.validate(vertx);

        bootstrap.childHandler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            if (isPaused()) {
              ch.close();
              return;
            }
            ChannelPipeline pipeline = ch.pipeline();
            NetServerBase.this.initChannel(ch.pipeline());
            pipeline.addLast("handler", new ServerHandler(ch));
          }
        });

        applyConnectionOptions(bootstrap);

        handlerManager.addHandler(handler, listenContext);

        try {
          bindFuture = AsyncResolveConnectHelper.doBind(vertx, port, host, bootstrap);
          bindFuture.addListener(res -> {
            if (res.succeeded()) {
              Channel ch = res.result();
              log.trace("Net server listening on " + host + ":" + ch.localAddress());
              // Update port to actual port - wildcard port 0 might have been used
              NetServerBase.this.actualPort = ((InetSocketAddress)ch.localAddress()).getPort();
              NetServerBase.this.id = new ServerID(NetServerBase.this.actualPort, id.host);
              serverChannelGroup.add(ch);
              vertx.sharedNetServers().put(id, NetServerBase.this);
              metrics = vertx.metricsSPI().createMetrics(new SocketAddressImpl(id.port, id.host), options);
            } else {
              vertx.sharedNetServers().remove(id);
            }
          });

        } catch (Throwable t) {
          // Make sure we send the exception back through the handler (if any)
          if (listenHandler != null) {
            vertx.runOnContext(v ->  listenHandler.handle(Future.failedFuture(t)));
          } else {
            // No handler - log so user can see failure
            log.error(t);
          }
          listening = false;
          return;
        }
        if (port != 0) {
          vertx.sharedNetServers().put(id, this);
        }
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = shared;
        this.actualPort = shared.actualPort();
        metrics = vertx.metricsSPI().createMetrics(new SocketAddressImpl(id.port, id.host), options);
        actualServer.handlerManager.addHandler(handler, listenContext);
      }

      // just add it to the future so it gets notified once the bind is complete
      actualServer.bindFuture.addListener(res -> {
        if (listenHandler != null) {
          AsyncResult<Void> ares;
          if (res.succeeded()) {
            ares = Future.succeededFuture();
          } else {
            listening = false;
            ares = Future.failedFuture(res.cause());
          }
          // Call with expectRightThread = false as if server is already listening
          // Netty will call future handler immediately with calling thread
          // which might be a non Vert.x thread (if running embedded)
          listenContext.runOnContext(v -> listenHandler.handle(ares));
        } else if (res.failed()) {
          // No handler - log so user can see failure
          log.error("Failed to listen", res.cause());
          listening = false;
        }
      });
    }
    return;
  }

  public synchronized void close() {
    close(null);
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> done) {
    ContextImpl context = vertx.getOrCreateContext();
    if (!listening) {
      if (done != null) {
        executeCloseDone(context, done, null);
      }
      return;
    }
    listening = false;
    synchronized (vertx.sharedNetServers()) {

      if (actualServer != null) {
        actualServer.handlerManager.removeHandler(registeredHandler, listenContext);

        if (actualServer.handlerManager.hasHandlers()) {
          // The actual server still has handlers so we don't actually close it
          if (done != null) {
            executeCloseDone(context, done, null);
          }
        } else {
          // No Handlers left so close the actual server
          // The done handler needs to be executed on the context that calls close, NOT the context
          // of the actual server
          actualServer.actualClose(context, done);
        }
      }
    }
    if (creatingContext != null) {
      creatingContext.removeCloseHook(this);
    }
  }

  public synchronized int actualPort() {
    return actualPort;
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null && metrics.isEnabled();
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  private void actualClose(ContextImpl closeContext, Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedNetServers().remove(id);
    }

    ContextImpl currCon = vertx.getContext();

    for (C sock : socketMap.values()) {
      sock.close();
    }

    // Sanity check
    if (vertx.getContext() != currCon) {
      throw new IllegalStateException("Context was changed");
    }

    ChannelGroupFuture fut = serverChannelGroup.close();
    fut.addListener(cg -> {
      if (metrics != null) {
        metrics.close();
      }
      executeCloseDone(closeContext, done, fut.cause());
    });

  }

  private void executeCloseDone(ContextImpl closeContext, Handler<AsyncResult<Void>> done, Exception e) {
    if (done != null) {
      Future<Void> fut = e == null ? Future.succeededFuture() : Future.failedFuture(e);
      closeContext.runOnContext(v -> done.handle(fut));
    }
  }

  private class ServerHandler extends VertxNetHandler<C> {
    public ServerHandler(Channel ch) {
      super(ch, socketMap);
    }

    @Override
    protected void handleMsgReceived(C conn, Object msg) {
      NetServerBase.this.handleMsgReceived(conn, msg);
    }

    @Override
    protected Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception {
      return NetServerBase.this.safeObject(msg, allocator);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      Channel ch = ctx.channel();
      EventLoop worker = ch.eventLoop();

      //Choose a handler
      HandlerHolder<Handler<? super C>> handler = handlerManager.chooseHandler(worker);
      if (handler == null) {
        //Ignore
        return;
      }

      if (sslHelper.isSSL()) {
        GenericFutureListener<io.netty.util.concurrent.Future<? super Channel>> handshakeListener = future -> {
          if (future.isSuccess()) {
            connected(ch, handler);
          } else {
            log.error("Client from origin " + ch.remoteAddress() + " failed to connect over ssl: " + future.cause());
          }
        };
        ChannelOutboundHandler sslHandler;
        if (options.getSni()) {
          sslHandler = new VertxSniHandler(sslHelper, vertx, handshakeListener);
        } else {
          sslHandler = new SslHandler(sslHelper.createEngine(vertx));
          ((SslHandler)sslHandler).handshakeFuture().addListener(handshakeListener);
        }
        ch.pipeline().addFirst("ssl", sslHandler);
      } else {
        connected(ch, handler);
      }
    }

    private void connected(Channel ch, HandlerHolder<Handler<? super C>> handler) {
      // Need to set context before constructor is called as writehandler registration needs this
      ContextImpl.setContext(handler.context);
      C sock = createConnection(vertx, ch, handler.context, sslHelper, metrics);
      socketMap.put(ch, sock);
      VertxNetHandler netHandler = ch.pipeline().get(VertxNetHandler.class);
      netHandler.conn = sock;
      handler.context.executeFromIO(() -> {
        sock.metric(metrics.connected(sock.remoteAddress(), sock.remoteName()));
        handler.handler.handle(sock);
      });
    }
  }

  protected abstract void handleMsgReceived(C conn, Object msg);

  protected abstract Object safeObject(Object msg, ByteBufAllocator allocator);

  /**
   * Create a connection for a channel.
   *
   * @return the created connection
   */
  protected abstract C createConnection(VertxInternal vertx, Channel channel, ContextImpl context,
                                        SSLHelper helper, TCPMetrics metrics);

  /**
   * Apply the connection option to the server.
   *
   * @param bootstrap the Netty server bootstrap
   */
  protected void applyConnectionOptions(ServerBootstrap bootstrap) {
    bootstrap.childOption(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    if (options.getSendBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    if (options.getSoLinger() != -1) {
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    }
    if (options.getTrafficClass() != -1) {
      bootstrap.childOption(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);

    bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    if (options.getAcceptBacklog() != -1) {
      bootstrap.option(ChannelOption.SO_BACKLOG, options.getAcceptBacklog());
    }
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
