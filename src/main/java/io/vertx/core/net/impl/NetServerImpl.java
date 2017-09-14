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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.impl.HttpHandlers;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.streams.ReadStream;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetServerImpl implements Closeable, MetricsProvider, NetServer {

  private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);

  protected final VertxInternal vertx;
  protected final NetServerOptions options;
  protected final ContextImpl creatingContext;
  protected final SSLHelper sslHelper;
  protected final boolean logEnabled;
  private final Map<Channel, NetSocketImpl> socketMap = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<Handlers> handlerManager = new HandlerManager<>(availableWorkers);
  private final NetSocketStream connectStream = new NetSocketStream();
  private ChannelGroup serverChannelGroup;
  private boolean paused;
  private volatile boolean listening;
  private Handler<NetSocket> registeredHandler;
  private volatile ServerID id;
  private NetServerImpl actualServer;
  private AsyncResolveConnectHelper bindFuture;
  private volatile int actualPort;
  private ContextImpl listenContext;
  private TCPMetrics metrics;
  private Handler<NetSocket> handler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;

  public NetServerImpl(VertxInternal vertx, NetServerOptions options) {
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

  @Override
  public synchronized Handler<NetSocket> connectHandler() {
    return handler;
  }

  @Override
  public synchronized NetServer connectHandler(Handler<NetSocket> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set connectHandler when server is listening");
    }
    this.handler = handler;
    return this;
  }

  @Override
  public synchronized NetServer exceptionHandler(Handler<Throwable> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set exceptionHandler when server is listening");
    }
    this.exceptionHandler = handler;
    return this;
  }

  protected void initChannel(ChannelPipeline pipeline) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    if (sslHelper.isSSL()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
  }

  public synchronized void listen(Handler<NetSocket> handler, int port, String host, Handler<AsyncResult<Void>> listenHandler) {
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
      NetServerImpl shared = vertx.sharedNetServers().get(id);
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
            HandlerHolder<Handlers> handler = handlerManager.chooseHandler(ch.eventLoop());
            if (handler != null) {
              if (sslHelper.isSSL()) {
                io.netty.util.concurrent.Future<Channel> handshakeFuture;
                if (options.isSni()) {
                  VertxSniHandler sniHandler = new VertxSniHandler(sslHelper, vertx);
                  handshakeFuture = sniHandler.handshakeFuture();
                  ch.pipeline().addFirst("ssl", sniHandler);
                } else {
                  SslHandler sslHandler = new SslHandler(sslHelper.createEngine(vertx));
                  handshakeFuture = sslHandler.handshakeFuture();
                  ch.pipeline().addFirst("ssl", sslHandler);
                }
                handshakeFuture.addListener(future -> {
                  if (future.isSuccess()) {
                    connected(handler, ch);
                  } else {
                    Handler<Throwable> exceptionHandler = handler.handler.exceptionHandler;
                    if (exceptionHandler != null) {
                      handler.context.executeFromIO(() -> {
                        exceptionHandler.handle(future.cause());
                      });
                    } else {
                      log.error("Client from origin " + ch.remoteAddress() + " failed to connect over ssl: " + future.cause());
                    }
                  }
                });
              } else {
                connected(handler, ch);
              }
            }
          }
        });

        applyConnectionOptions(bootstrap);

        handlerManager.addHandler(new Handlers(handler, exceptionHandler), listenContext);

        try {
          bindFuture = AsyncResolveConnectHelper.doBind(vertx, port, host, bootstrap);
          bindFuture.addListener(res -> {
            if (res.succeeded()) {
              Channel ch = res.result();
              log.trace("Net server listening on " + host + ":" + ch.localAddress());
              // Update port to actual port - wildcard port 0 might have been used
              NetServerImpl.this.actualPort = ((InetSocketAddress)ch.localAddress()).getPort();
              NetServerImpl.this.id = new ServerID(NetServerImpl.this.actualPort, id.host);
              serverChannelGroup.add(ch);
              vertx.sharedNetServers().put(id, NetServerImpl.this);
              VertxMetrics metrics = vertx.metricsSPI();
              if (metrics != null) {
                this.metrics = metrics.createMetrics(new SocketAddressImpl(id.port, id.host), options);
              }
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
        VertxMetrics metrics = vertx.metricsSPI();
        this.metrics = metrics != null ? metrics.createMetrics(new SocketAddressImpl(id.port, id.host), options) : null;
        actualServer.handlerManager.addHandler(new Handlers(handler, exceptionHandler), listenContext);
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
  public NetServer listen(int port, String host) {
    return listen(port, host, null);
  }

  @Override
  public NetServer listen(int port) {
    return listen(port, "0.0.0.0", null);
  }

  @Override
  public NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(port, "0.0.0.0", listenHandler);
  }

  @Override
  public NetServer listen() {
    listen(null);
    return this;
  }

  @Override
  public synchronized NetServer listen(int port, String host, Handler<AsyncResult<NetServer>> listenHandler) {
    listen(handler, port, host, ar -> {
      if (listenHandler != null) {
        listenHandler.handle(ar.map(this));
      }
    });
    return this;
  }

  @Override
  public synchronized NetServer listen(Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(options.getPort(), options.getHost(), listenHandler);
  }

  @Override
  public ReadStream<NetSocket> connectStream() {
    return connectStream;
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> done) {
    if (endHandler != null) {
      Handler<Void> handler = endHandler;
      endHandler = null;
      Handler<AsyncResult<Void>> next = done;
      done = event -> {
        if (event.succeeded()) {
          handler.handle(event.result());
        }
        if (next != null) {
          next.handle(event);
        }
      };
    }
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
        actualServer.handlerManager.removeHandler(new Handlers(registeredHandler, exceptionHandler), listenContext);

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
    return metrics != null;
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

    for (NetSocketImpl sock : socketMap.values()) {
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

  private void connected(HandlerHolder<Handlers> handler, Channel ch) {
    EventLoop worker = ch.eventLoop();
    // Need to set context before constructor is called as writehandler registration needs this
    ContextImpl.setContext(handler.context);

    NetServerImpl.this.initChannel(ch.pipeline());

    VertxNetHandler nh = new VertxNetHandler(ctx -> new NetSocketImpl(vertx, ctx, handler.context, sslHelper, metrics)) {
      @Override
      protected void handleMessage(NetSocketImpl connection, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {
        connection.handleMessageReceived(msg);
      }
    };
    nh.addHandler(conn -> socketMap.put(ch, conn));
    nh.removeHandler(conn -> socketMap.remove(ch));
    ch.pipeline().addLast("handler", nh);
    NetSocketImpl sock = nh.getConnection();
    handler.context.executeFromIO(() -> {
      if (metrics != null) {
        sock.metric(metrics.connected(sock.remoteAddress(), sock.remoteName()));
      }
      handler.handler.connectionHandler.handle(sock);
    });
  }

  private void executeCloseDone(ContextImpl closeContext, Handler<AsyncResult<Void>> done, Exception e) {
    if (done != null) {
      Future<Void> fut = e == null ? Future.succeededFuture() : Future.failedFuture(e);
      closeContext.runOnContext(v -> done.handle(fut));
    }
  }

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

  /*
          Needs to be protected using the NetServerImpl monitor as that protects the listening variable
          In practice synchronized overhead should be close to zero assuming most access is from the same thread due
          to biased locks
        */
  private class NetSocketStream implements ReadStream<NetSocket> {

    @Override
    public NetSocketStream handler(Handler<NetSocket> handler) {
      connectHandler(handler);
      return this;
    }

    @Override
    public NetSocketStream pause() {
      pauseAccepting();
      return this;
    }

    @Override
    public NetSocketStream resume() {
      resumeAccepting();
      return this;
    }

    @Override
    public NetSocketStream endHandler(Handler<Void> handler) {
      synchronized (NetServerImpl.this) {
        endHandler = handler;
        return this;
      }
    }

    @Override
    public NetSocketStream exceptionHandler(Handler<Throwable> handler) {
      // Should we use it in the server close exception handler ?
      return this;
    }
  }

  static class Handlers {
    final Handler<NetSocket> connectionHandler;
    final Handler<Throwable> exceptionHandler;
    public Handlers(Handler<NetSocket> connectionHandler, Handler<Throwable> exceptionHandler) {
      this.connectionHandler = connectionHandler;
      this.exceptionHandler = exceptionHandler;
    }
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Handlers that = (Handlers) o;

      if (!Objects.equals(connectionHandler, that.connectionHandler)) return false;
      if (!Objects.equals(exceptionHandler, that.exceptionHandler)) return false;

      return true;
    }
    public int hashCode() {
      int result = 0;
      if (connectionHandler != null) {
        result = 31 * result + connectionHandler.hashCode();
      }
      if (exceptionHandler != null) {
        result = 31 * result + exceptionHandler.hashCode();
      }
      return result;
    }
  }
}
