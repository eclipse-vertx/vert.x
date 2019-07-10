/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.streams.ReadStream;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl implements HttpServer, Closeable, MetricsProvider {

  static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);

  private static final Handler<Throwable> DEFAULT_EXCEPTION_HANDLER = t -> log.trace("Connection failure", t);

  private static final String FLASH_POLICY_HANDLER_PROP_NAME = "vertx.flashPolicyHandler";
  private static final String DISABLE_WEBSOCKETS_PROP_NAME = "vertx.disableWebsockets";
  private static final String DISABLE_H2C_PROP_NAME = "vertx.disableH2c";

  static final boolean USE_FLASH_POLICY_HANDLER = Boolean.getBoolean(FLASH_POLICY_HANDLER_PROP_NAME);
  static final boolean DISABLE_WEBSOCKETS = Boolean.getBoolean(DISABLE_WEBSOCKETS_PROP_NAME);

  final HttpServerOptions options;
  final VertxInternal vertx;
  private final SSLHelper sslHelper;
  private final ContextInternal creatingContext;
  private final boolean disableH2c = Boolean.getBoolean(DISABLE_H2C_PROP_NAME);
  final Map<Channel, ConnectionBase> connectionMap = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<HttpHandlers> httpHandlerMgr = new HandlerManager<>(availableWorkers);
  private final HttpStreamHandler<ServerWebSocket> wsStream = new HttpStreamHandler<>();
  private final HttpStreamHandler<HttpServerRequest> requestStream = new HttpStreamHandler<>();
  private Handler<HttpConnection> connectionHandler;

  private ChannelGroup serverChannelGroup;
  private volatile boolean listening;
  private AsyncResolveConnectHelper bindFuture;
  private ServerID id;
  private HttpServerImpl actualServer;
  private volatile int actualPort;
  private ContextInternal listenContext;
  HttpServerMetrics metrics;
  private Handler<Throwable> exceptionHandler;

  public HttpServerImpl(VertxInternal vertx, HttpServerOptions options) {
    this.options = new HttpServerOptions(options);
    this.vertx = vertx;
    this.creatingContext = vertx.getContext();
    if (creatingContext != null) {
      creatingContext.addCloseHook(this);
    }
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
  }

  @Override
  public synchronized HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    requestStream.handler(handler);
    return this;
  }

  @Override
  public ReadStream<HttpServerRequest> requestStream() {
    return requestStream;
  }

  @Override
  public HttpServer websocketHandler(Handler<ServerWebSocket> handler) {
    websocketStream().handler(handler);
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return requestStream.handler();
  }

  @Override
  public synchronized HttpServer connectionHandler(Handler<HttpConnection> handler) {
    if (listening) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    connectionHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpServer exceptionHandler(Handler<Throwable> handler) {
    if (listening) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    exceptionHandler = handler;
    return this;
  }

  @Override
  public Handler<ServerWebSocket> websocketHandler() {
    return wsStream.handler();
  }

  @Override
  public ReadStream<ServerWebSocket> websocketStream() {
    return wsStream;
  }

  @Override
  public Future<HttpServer> listen() {
    Promise<HttpServer> promise = Promise.promise();
    listen(options.getPort(), options.getHost(), promise);
    return promise.future();
  }

  @Override
  public HttpServer listen(Handler<AsyncResult<HttpServer>> listenHandler) {
    return listen(options.getPort(), options.getHost(), listenHandler);
  }

  @Override
  public Future<HttpServer> listen(int port, String host) {
    Promise<HttpServer> promise = Promise.promise();
    listen(port, host, promise);
    return promise.future();
  }

  @Override
  public Future<HttpServer> listen(int port) {
    Promise<HttpServer> promise = Promise.promise();
    listen(port, "0.0.0.0", promise);
    return promise.future();
  }

  @Override
  public HttpServer listen(int port, Handler<AsyncResult<HttpServer>> listenHandler) {
    return listen(port, "0.0.0.0", listenHandler);
  }

  public HttpServer listen(int port, String host, Handler<AsyncResult<HttpServer>> listenHandler) {
    return listen(SocketAddress.inetSocketAddress(port, host), listenHandler);
  }

  @Override
  public Future<HttpServer> listen(SocketAddress address) {
    Promise<HttpServer> promise = Promise.promise();
    listen(address, promise);
    return promise.future();
  }

  private ChannelHandler childHandler(SocketAddress address, String serverOrigin) {
    VertxMetrics vertxMetrics = vertx.metricsSPI();
    this.metrics = vertxMetrics != null ? vertxMetrics.createHttpServerMetrics(options, address) : null;
    return new HttpServerChannelInitializer(
      vertx,
      sslHelper,
      options,
      serverOrigin,
      metrics,
      disableH2c,
      httpHandlerMgr::chooseHandler,
      eventLoop -> {
        HandlerHolder<HttpHandlers> holder = httpHandlerMgr.chooseHandler(eventLoop);
        if (holder != null && holder.handler.exceptionHandler != null) {
          return new HandlerHolder<>(holder.context, holder.handler.exceptionHandler);
        } else {
          return null;
        }
      }) {
      @Override
      protected void initChannel(Channel ch) {
        if (!requestStream.accept() || !wsStream.accept()) {
          ch.close();
        } else {
          super.initChannel(ch);
        }
      }
    };
  }

  public synchronized HttpServer listen(SocketAddress address, Handler<AsyncResult<HttpServer>> listenHandler) {
    if (requestStream.handler() == null && wsStream.handler() == null) {
      throw new IllegalStateException("Set request or websocket handler first");
    }
    if (listening) {
      throw new IllegalStateException("Already listening");
    }
    listenContext = vertx.getOrCreateContext();
    listening = true;
    String host = address.host() != null ? address.host() : "localhost";
    int port = address.port();
    List<HttpVersion> applicationProtocols = options.getAlpnVersions();
    if (listenContext.isWorkerContext()) {
      applicationProtocols =  applicationProtocols.stream().filter(v -> v != HttpVersion.HTTP_2).collect(Collectors.toList());
    }
    sslHelper.setApplicationProtocols(applicationProtocols);
    synchronized (vertx.sharedHttpServers()) {
      this.actualPort = port; // Will be updated on bind for a wildcard port
      id = new ServerID(port, host);
      HttpServerImpl shared = vertx.sharedHttpServers().get(id);
      if (shared == null || port == 0) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(vertx.getAcceptorEventLoopGroup(), availableWorkers);
        applyConnectionOptions(address.path() != null, bootstrap);
        sslHelper.validate(vertx);
        String serverOrigin = (options.isSsl() ? "https" : "http") + "://" + host + ":" + port;
        bootstrap.childHandler(childHandler(address, serverOrigin));
        addHandlers(this, listenContext);
        try {
          bindFuture = AsyncResolveConnectHelper.doBind(vertx, address, bootstrap);
          bindFuture.addListener(res -> {
            if (res.failed()) {
              vertx.sharedHttpServers().remove(id);
            } else {
              Channel serverChannel = res.result();
              if (serverChannel.localAddress() instanceof InetSocketAddress) {
                HttpServerImpl.this.actualPort = ((InetSocketAddress)serverChannel.localAddress()).getPort();
              } else {
                HttpServerImpl.this.actualPort = address.port();
              }
              serverChannelGroup.add(serverChannel);
            }
          });
        } catch (final Throwable t) {
          // Make sure we send the exception back through the handler (if any)
          if (listenHandler != null) {
            vertx.runOnContext(v -> listenHandler.handle(Future.failedFuture(t)));
          } else {
            // No handler - log so user can see failure
            log.error(t);
          }
          listening = false;
          return this;
        }
        vertx.sharedHttpServers().put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = shared;
        this.actualPort = shared.actualPort;
        addHandlers(actualServer, listenContext);
        VertxMetrics metrics = vertx.metricsSPI();
        this.metrics = metrics != null ? metrics.createHttpServerMetrics(options, address) : null;
      }
      actualServer.bindFuture.addListener(future -> {
        if (listenHandler != null) {
          final AsyncResult<HttpServer> res;
          if (future.succeeded()) {
            res = Future.succeededFuture(HttpServerImpl.this);
          } else {
            res = Future.failedFuture(future.cause());
            listening = false;
          }
          listenContext.runOnContext((v) -> listenHandler.handle(res));
        } else if (future.failed()) {
          listening  = false;
          if (metrics != null) {
            metrics.close();
            metrics = null;
          }
          // No handler - log so user can see failure
          log.error(future.cause());
        }
      });
    }
    return this;
  }

  /**
   * Internal method that closes all servers when Vert.x is closing
   */
  public void closeAll(Handler<AsyncResult<Void>> handler) {
    List<HttpHandlers> list = httpHandlerMgr.handlers();
    List<Future> futures = list.stream()
      .<Future<Void>>map(handlers -> Future.future(handlers.server::close))
      .collect(Collectors.toList());
    CompositeFuture fut = CompositeFuture.all(futures);
    fut.setHandler(ar -> handler.handle(ar.mapEmpty()));
  }

  @Override
  public Future<Void> close() {
    Promise<Void> promise = Promise.promise();
    close(promise);
    return promise.future();
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> done) {
    if (wsStream.endHandler() != null || requestStream.endHandler() != null) {
      Handler<Void> wsEndHandler = wsStream.endHandler();
      wsStream.endHandler(null);
      Handler<Void> requestEndHandler = requestStream.endHandler();
      requestStream.endHandler(null);
      Handler<AsyncResult<Void>> next = done;
      done = event -> {
          if (event.succeeded()) {
            if (wsEndHandler != null) {
              wsEndHandler.handle(event.result());
            }
            if (requestEndHandler != null) {
              requestEndHandler.handle(event.result());
            }
          }
          if (next != null) {
            next.handle(event);
          }
      };
    }

    ContextInternal context = vertx.getOrCreateContext();
    if (!listening) {
      executeCloseDone(context, done, null);
      return;
    }
    listening = false;

    synchronized (vertx.sharedHttpServers()) {

      if (actualServer != null) {

        actualServer.httpHandlerMgr.removeHandler(
          new HttpHandlers(
            this,
            requestStream.handler(),
            wsStream.handler(),
            connectionHandler,
            exceptionHandler == null ? DEFAULT_EXCEPTION_HANDLER : exceptionHandler)
          , listenContext);

        if (actualServer.httpHandlerMgr.hasHandlers()) {
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
      } else {
        executeCloseDone(context, done, null);
      }
    }
    if (creatingContext != null) {
      creatingContext.removeCloseHook(this);
    }
  }

  public synchronized boolean isClosed() {
    return !listening;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  public SSLHelper getSslHelper() {
    return sslHelper;
  }

  private void applyConnectionOptions(boolean domainSocket, ServerBootstrap bootstrap) {
    vertx.transport().configure(options, domainSocket, bootstrap);
  }


  private void addHandlers(HttpServerImpl server, ContextInternal context) {
    server.httpHandlerMgr.addHandler(
      new HttpHandlers(
        this,
        requestStream.handler(),
        wsStream.handler(),
        connectionHandler,
        exceptionHandler == null ? DEFAULT_EXCEPTION_HANDLER : exceptionHandler)
      , context);
  }

  private void actualClose(final ContextInternal closeContext, final Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedHttpServers().remove(id);
    }

    ContextInternal currCon = vertx.getContext();

    for (ConnectionBase conn : connectionMap.values()) {
      conn.close();
    }

    // Sanity check
    if (vertx.getContext() != currCon) {
      throw new IllegalStateException("Context was changed");
    }

    if (metrics != null) {
      metrics.close();
    }

    ChannelGroupFuture fut = serverChannelGroup.close();
    fut.addListener(cgf -> executeCloseDone(closeContext, done, fut.cause()));
  }

  @Override
  public int actualPort() {
    return actualPort;
  }

  private void executeCloseDone(final ContextInternal closeContext, final Handler<AsyncResult<Void>> done, final Exception e) {
    if (done != null) {
      Future<Void> fut = e != null ? Future.failedFuture(e) : Future.succeededFuture();
      closeContext.runOnContext((v) -> done.handle(fut));
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
    Needs to be protected using the HttpServerImpl monitor as that protects the listening variable
    In practice synchronized overhead should be close to zero assuming most access is from the same thread due
    to biased locks
  */
  class HttpStreamHandler<C extends ReadStream<Buffer>> implements ReadStream<C> {

    private Handler<C> handler;
    private long demand = Long.MAX_VALUE;
    private Handler<Void> endHandler;

    Handler<C> handler() {
      synchronized (HttpServerImpl.this) {
        return handler;
      }
    }

    boolean accept() {
      synchronized (HttpServerImpl.this) {
        boolean accept = demand > 0L;
        if (accept && demand != Long.MAX_VALUE) {
          demand--;
        }
        return accept;
      }
    }

    Handler<Void> endHandler() {
      synchronized (HttpServerImpl.this) {
        return endHandler;
      }
    }

    @Override
    public ReadStream handler(Handler<C> handler) {
      synchronized (HttpServerImpl.this) {
        if (listening) {
          throw new IllegalStateException("Please set handler before server is listening");
        }
        this.handler = handler;
        return this;
      }
    }

    @Override
    public ReadStream pause() {
      synchronized (HttpServerImpl.this) {
        demand = 0L;
        return this;
      }
    }

    @Override
    public ReadStream fetch(long amount) {
      if (amount > 0L) {
        demand += amount;
        if (demand < 0L) {
          demand = Long.MAX_VALUE;
        }
      }
      return this;
    }

    @Override
    public ReadStream resume() {
      synchronized (HttpServerImpl.this) {
        demand = Long.MAX_VALUE;
        return this;
      }
    }

    @Override
    public ReadStream endHandler(Handler<Void> endHandler) {
      synchronized (HttpServerImpl.this) {
        this.endHandler = endHandler;
        return this;
      }
    }

    @Override
    public ReadStream exceptionHandler(Handler<Throwable> handler) {
      // Should we use it in the server close exception handler ?
      return this;
    }
  }

}
