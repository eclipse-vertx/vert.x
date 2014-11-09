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
import io.netty.channel.ChannelFuture;
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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.metrics.spi.NetMetrics;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.NetSocketStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetServerImpl implements NetServer, Closeable {

  private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);

  private final VertxInternal vertx;
  private final NetServerOptions options;
  private final ContextImpl creatingContext;
  private final SSLHelper sslHelper;
  private final NetMetrics metrics;
  private final Map<Channel, NetSocketImpl> socketMap = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<NetSocket> handlerManager = new HandlerManager<>(availableWorkers);
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private ServerID id;
  private NetServerImpl actualServer;
  private ChannelFuture bindFuture;
  private int actualPort;
  private Queue<Runnable> bindListeners = new ConcurrentLinkedQueue<>();
  private boolean listenersRun;
  private ContextImpl listenContext;
  private NetSocketStreamImpl connectStream = new NetSocketStreamImpl();

  public NetServerImpl(VertxInternal vertx, NetServerOptions options) {
    this.vertx = vertx;
    this.options = new NetServerOptions(options);
    this.sslHelper = new SSLHelper(options, KeyStoreHelper.create(vertx, options.getKeyStoreOptions()), KeyStoreHelper.create(vertx, options.getTrustStoreOptions()));
    this.creatingContext = vertx.getContext();
    if (creatingContext != null) {
      if (creatingContext.isMultiThreaded()) {
        throw new IllegalStateException("Cannot use NetServer in a multi-threaded worker verticle");
      }
      creatingContext.addCloseHook(this);
    }
    this.metrics = vertx.metricsSPI().createMetrics(this, options);
  }

  @Override
  public NetServer connectHandler(Handler<NetSocket> handler) {
    connectStream.handler(handler);
    return this;
  }

  @Override
  public Handler<NetSocket> connectHandler() {
    return connectStream.handler;
  }

  @Override
  public NetSocketStream connectStream() {
    return connectStream;
  }

  @Override
  public synchronized NetServer listen() {
    listen(null);
    return this;
  }

  @Override
  public synchronized NetServer listen(Handler<AsyncResult<NetServer>> listenHandler) {
    if (connectStream.handler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;

    listenContext = vertx.getOrCreateContext();

    synchronized (vertx.sharedNetServers()) {
      this.actualPort = options.getPort(); // Will be updated on bind for a wildcard port
      id = new ServerID(options.getPort(), options.getHost());
      NetServerImpl shared = vertx.sharedNetServers().get(id);
      if (shared == null || options.getPort() == 0) { // Wildcard port will imply a new actual server each time
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(availableWorkers);
        bootstrap.channel(NioServerSocketChannel.class);
        sslHelper.validate(vertx);

        bootstrap.childHandler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            if (connectStream.paused) {
              ch.close();
              return;
            }
            ChannelPipeline pipeline = ch.pipeline();
            if (sslHelper.isSSL()) {
              SslHandler sslHandler = sslHelper.createSslHandler(vertx, false);
              pipeline.addLast("ssl", sslHandler);
            }
            if (sslHelper.isSSL()) {
              // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
              pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
            }
            if (options.getIdleTimeout() > 0) {
              pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
            }
            pipeline.addLast("handler", new ServerHandler());
          }
        });

        applyConnectionOptions(bootstrap);

        if (connectStream.handler != null) {
          handlerManager.addHandler(connectStream, listenContext);
        }

        try {
          InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName(options.getHost()), options.getPort());
          bindFuture = bootstrap.bind(addr).addListener(future -> runListeners());
          this.addListener(() -> {
            if (bindFuture.isSuccess()) {
              log.trace("Net server listening on " + options.getHost() + ":" + bindFuture.channel().localAddress());
              // Update port to actual port - wildcard port 0 might have been used
              NetServerImpl.this.actualPort = ((InetSocketAddress)bindFuture.channel().localAddress()).getPort();
              NetServerImpl.this.id = new ServerID(NetServerImpl.this.actualPort, id.host);
              vertx.sharedNetServers().put(id, NetServerImpl.this);
              metrics.listening(new SocketAddressImpl(id.port, id.host));
            } else {
              vertx.sharedNetServers().remove(id);
            }
          });
          serverChannelGroup.add(bindFuture.channel());
        } catch (Throwable t) {
          // Make sure we send the exception back through the handler (if any)
          if (listenHandler != null) {
            vertx.runOnContext(v ->  listenHandler.handle(Future.completedFuture(t)));
          } else {
            // No handler - log so user can see failure
            log.error(t);
          }
          listening = false;
          return this;
        }
        if (options.getPort() != 0) {
          vertx.sharedNetServers().put(id, this);
        }
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = shared;
        this.actualPort = shared.actualPort();
        metrics.listening(new SocketAddressImpl(id.port, id.host));
        if (connectStream.handler != null) {
          actualServer.handlerManager.addHandler(connectStream, listenContext);
        }
      }

      // just add it to the future so it gets notified once the bind is complete
      actualServer.addListener(() -> {
        if (listenHandler != null) {
          AsyncResult<NetServer> res;
          if (actualServer.bindFuture.isSuccess()) {
            res = Future.completedFuture(NetServerImpl.this);
          } else {
            listening = false;
            res = Future.completedFuture(actualServer.bindFuture.cause());
          }
          // Call with expectRightThread = false as if server is already listening
          // Netty will call future handler immediately with calling thread
          // which might be a non Vert.x thread (if running embedded)
          listenContext.execute(() -> listenHandler.handle(res), false);
        } else if (!actualServer.bindFuture.isSuccess()) {
          // No handler - log so user can see failure
          log.error(actualServer.bindFuture.cause());
          listening = false;
        }
      });
    }
    return this;
  }

  public synchronized void close() {
    close(null);
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> done) {
    if (connectStream.endHandler != null) {
      Handler<AsyncResult<Void>> next = done;
      done = new AsyncResultHandler<Void>() {
        @Override
        public void handle(AsyncResult<Void> event) {
          if (event.succeeded()) {
            connectStream.endHandler.handle(event.result());
          }
          if (next != null) {
            next.handle(event);
          }
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
        actualServer.handlerManager.removeHandler(connectStream, listenContext);

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

  @Override
  public synchronized int actualPort() {
    return actualPort;
  }

  @Override
  public String metricBaseName() {
    return metrics.baseName();
  }

  @Override
  public Map<String, JsonObject> metrics() {
    String name = metricBaseName();
    return vertx.metrics().entrySet().stream()
      .filter(e -> e.getKey().startsWith(name))
      .collect(Collectors.toMap(e -> e.getKey().substring(name.length() + 1), Map.Entry::getValue));
  }

  private void applyConnectionOptions(ServerBootstrap bootstrap) {
    bootstrap.childOption(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    if (options.getSendBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }

    bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    if (options.getTrafficClass() != -1) {
      bootstrap.childOption(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);

    bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    bootstrap.option(ChannelOption.SO_BACKLOG, options.getAcceptBacklog());
  }

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

  private void actualClose(ContextImpl closeContext, Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedNetServers().remove(id);
    }

    for (NetSocketImpl sock : socketMap.values()) {
      sock.close();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    vertx.setContext(closeContext);

    ChannelGroupFuture fut = serverChannelGroup.close();
    fut.addListener(cg -> {
      metrics.close();
      executeCloseDone(closeContext, done, fut.cause());
    });

  }

  private void executeCloseDone(ContextImpl closeContext, Handler<AsyncResult<Void>> done, Exception e) {
    if (done != null) {
      closeContext.execute(() -> done.handle(Future.completedFuture(e)), false);
    }
  }

  private class ServerHandler extends VertxNetHandler {
    public ServerHandler() {
      super(NetServerImpl.this.vertx, socketMap);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      Channel ch = ctx.channel();
      EventLoop worker = ch.eventLoop();

      //Choose a handler
      HandlerHolder<NetSocket> handler = handlerManager.chooseHandler(worker);
      if (handler == null) {
        //Ignore
        return;
      }

      if (sslHelper.isSSL()) {
        SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

        io.netty.util.concurrent.Future<Channel> fut = sslHandler.handshakeFuture();
        fut.addListener(future -> {
          if (future.isSuccess()) {
            connected(ch, handler);
          } else {
            log.error("Client from origin " + ch.remoteAddress() + " failed to connect over ssl");
          }
        });
      } else {
        connected(ch, handler);
      }
    }

    private void connected(Channel ch, HandlerHolder<NetSocket> handler) {
      handler.context.execute(() -> doConnected(ch, handler), true);
    }

    private void doConnected(Channel ch, HandlerHolder<NetSocket> handler) {
      NetSocketImpl sock = new NetSocketImpl(vertx, ch, handler.context, sslHelper, false, metrics);
      socketMap.put(ch, sock);
      handler.handler.handle(sock);
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

  class NetSocketStreamImpl implements Handler<NetSocket>, NetSocketStream {

    protected Handler<NetSocket> handler;
    private boolean paused;
    private Handler<Void> endHandler;

    @Override
    public void handle(NetSocket event) {
      handler.handle(event);
    }

    @Override
    public NetSocketStreamImpl handler(Handler<NetSocket> handler) {
      if (listening) {
        throw new IllegalStateException("Cannot set connectHandler when server is listening");
      }
      this.handler = handler;
      return this;
    }

    @Override
    public NetSocketStreamImpl pause() {
      if (!paused) {
        paused = true;
      }
      return this;
    }

    @Override
    public NetSocketStreamImpl resume() {
      if (paused) {
        paused = false;
      }
      return this;
    }

    @Override
    public NetSocketStreamImpl endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }

    @Override
    public NetSocketStreamImpl exceptionHandler(Handler<Throwable> handler) {
      // Should we use it in the server close exception handler ?
      return this;
    }
  }
}
