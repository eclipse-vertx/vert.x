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
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.streams.ReadStream;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
  private static final boolean USE_FLASH_POLICY_HANDLER = Boolean.getBoolean(FLASH_POLICY_HANDLER_PROP_NAME);
  private static final String DISABLE_WEBSOCKETS_PROP_NAME = "vertx.disableWebsockets";
  private static final boolean DISABLE_WEBSOCKETS = Boolean.getBoolean(DISABLE_WEBSOCKETS_PROP_NAME);
  private static final String DISABLE_H2C_PROP_NAME = "vertx.disableH2c";
  private final boolean DISABLE_H2C = Boolean.getBoolean(DISABLE_H2C_PROP_NAME);

  private final HttpServerOptions options;
  private final VertxInternal vertx;
  private final SSLHelper sslHelper;
  private final ContextInternal creatingContext;
  private final Map<Channel, ConnectionBase> connectionMap = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<HttpHandlers> httpHandlerMgr = new HandlerManager<>(availableWorkers);
  private final HttpStreamHandler<ServerWebSocket> wsStream = new HttpStreamHandler<>();
  private final HttpStreamHandler<HttpServerRequest> requestStream = new HttpStreamHandler<>();
  private Handler<HttpConnection> connectionHandler;
  private final String subProtocols;
  private String serverOrigin;

  private ChannelGroup serverChannelGroup;
  private volatile boolean listening;
  private AsyncResolveConnectHelper bindFuture;
  private ServerID id;
  private HttpServerImpl actualServer;
  private volatile int actualPort;
  private ContextInternal listenContext;
  private HttpServerMetrics metrics;
  private boolean logEnabled;
  private Handler<Throwable> exceptionHandler;

  public HttpServerImpl(VertxInternal vertx, HttpServerOptions options) {
    this.options = new HttpServerOptions(options);
    this.vertx = vertx;
    this.creatingContext = vertx.getContext();
    if (creatingContext != null) {
      if (creatingContext.isMultiThreadedWorkerContext()) {
        throw new IllegalStateException("Cannot use HttpServer in a multi-threaded worker verticle");
      }
      creatingContext.addCloseHook(this);
    }
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
    this.subProtocols = options.getWebsocketSubProtocols();
    this.logEnabled = options.getLogActivity();
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
  public HttpServer listen() {
    return listen(options.getPort(), options.getHost(), null);
  }

  @Override
  public HttpServer listen(Handler<AsyncResult<HttpServer>> listenHandler) {
    return listen(options.getPort(), options.getHost(), listenHandler);
  }

  @Override
  public HttpServer listen(int port, String host) {
    return listen(port, host, null);
  }

  @Override
  public HttpServer listen(int port) {
    return listen(port, "0.0.0.0", null);
  }

  @Override
  public HttpServer listen(int port, Handler<AsyncResult<HttpServer>> listenHandler) {
    return listen(port, "0.0.0.0", listenHandler);
  }

  public HttpServer listen(int port, String host, Handler<AsyncResult<HttpServer>> listenHandler) {
    return listen(SocketAddress.inetSocketAddress(port, host), listenHandler);
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
    serverOrigin = (options.isSsl() ? "https" : "http") + "://" + host + ":" + port;
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
        applyConnectionOptions(bootstrap);
        sslHelper.validate(vertx);
        bootstrap.childHandler(new ChannelInitializer<Channel>() {
          @Override
            protected void initChannel(Channel ch) throws Exception {
              if (!requestStream.accept() || !wsStream.accept()) {
                ch.close();
                return;
              }
              ChannelPipeline pipeline = ch.pipeline();
              if (sslHelper.isSSL()) {
                io.netty.util.concurrent.Future<Channel> handshakeFuture;
                if (options.isSni()) {
                  VertxSniHandler sniHandler = new VertxSniHandler(sslHelper, vertx);
                  pipeline.addLast(sniHandler);
                  handshakeFuture = sniHandler.handshakeFuture();
                } else {
                  SslHandler handler = new SslHandler(sslHelper.createEngine(vertx));
                  pipeline.addLast("ssl", handler);
                  handshakeFuture = handler.handshakeFuture();
                }
                handshakeFuture.addListener(future -> {
                  if (future.isSuccess()) {
                    if (options.isUseAlpn()) {
                      SslHandler sslHandler = pipeline.get(SslHandler.class);
                      String protocol = sslHandler.applicationProtocol();
                      if ("h2".equals(protocol)) {
                        handleHttp2(ch);
                      } else {
                        handleHttp1(ch);
                      }
                    } else {
                      handleHttp1(ch);
                    }
                  } else {
                    HandlerHolder<HttpHandlers> handler = httpHandlerMgr.chooseHandler(ch.eventLoop());
                    handler.context.executeFromIO(v -> {
                      handler.handler.exceptionHandler.handle(future.cause());
                    });
                  }
                });
              } else {
                if (DISABLE_H2C) {
                  handleHttp1(ch);
                } else {
                  IdleStateHandler idle;
                  if (options.getIdleTimeout() > 0) {
                    pipeline.addLast("idle", idle = new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
                  } else {
                    idle = null;
                  }
                  // Handler that detects whether the HTTP/2 connection preface or just process the request
                  // with the HTTP 1.x pipeline to support H2C with prior knowledge, i.e a client that connects
                  // and uses HTTP/2 in clear text directly without an HTTP upgrade.
                  pipeline.addLast(new Http1xOrH2CHandler() {
                    @Override
                    protected void configure(ChannelHandlerContext ctx, boolean h2c) {
                      if (idle != null) {
                        // It will be re-added but this way we don't need to pay attention to order
                        pipeline.remove(idle);
                      }
                      if (h2c) {
                        handleHttp2(ctx.channel());
                      } else {
                        handleHttp1(ch);
                      }
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                      if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
                        ctx.close();
                      }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                      super.exceptionCaught(ctx, cause);
                      HandlerHolder<HttpHandlers> handler = httpHandlerMgr.chooseHandler(ctx.channel().eventLoop());
                      handler.context.executeFromIO(v -> handler.handler.exceptionHandler.handle(cause));
                    }
                  });
                }
              }
            }
        });

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
              VertxMetrics metrics = vertx.metricsSPI();
              this.metrics = metrics != null ? metrics.createHttpServerMetrics(options, address) : null;
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
          // No handler - log so user can see failure
          log.error(future.cause());
        }
      });
    }
    return this;
  }

  VertxHttp2ConnectionHandler<Http2ServerConnection> buildHttp2ConnectionHandler(HandlerHolder<HttpHandlers> holder) {
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ServerConnection>()
      .server(true)
      .useCompression(options.isCompressionSupported())
      .useDecompression(options.isDecompressionSupported())
      .compressionLevel(options.getCompressionLevel())
      .initialSettings(options.getInitialSettings())
      .connectionFactory(connHandler -> new Http2ServerConnection(holder.context, serverOrigin, connHandler, options, holder.handler.requestHandler, metrics))
      .logEnabled(logEnabled)
      .build();
    handler.addHandler(conn -> {
      connectionMap.put(conn.channel(), conn);
      if (metrics != null) {
        conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
      }
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (holder.handler.connectionHandler != null) {
        holder.context.executeFromIO(v -> {
          holder.handler.connectionHandler.handle(conn);
        });
      }
    });
    handler.removeHandler(conn -> {
      connectionMap.remove(conn.channel());
    });
    return handler;
  }

  private void configureHttp1(ChannelPipeline pipeline, HandlerHolder<HttpHandlers> holder) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    if (USE_FLASH_POLICY_HANDLER) {
      pipeline.addLast("flashpolicy", new FlashPolicyHandler());
    }
    pipeline.addLast("httpDecoder", new HttpRequestDecoder(options.getMaxInitialLineLength()
        , options.getMaxHeaderSize(), options.getMaxChunkSize(), false, options.getDecoderInitialBufferSize()));
    pipeline.addLast("httpEncoder", new VertxHttpResponseEncoder());
    if (options.isDecompressionSupported()) {
      pipeline.addLast("inflater", new HttpContentDecompressor(true));
    }
    if (options.isCompressionSupported()) {
      pipeline.addLast("deflater", new HttpChunkContentCompressor(options.getCompressionLevel()));
    }
    if (sslHelper.isSSL() || options.isCompressionSupported()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
    if (!DISABLE_H2C) {
      pipeline.addLast("h2c", new Http1xUpgradeToH2CHandler(this, httpHandlerMgr));
    }
    if (DISABLE_WEBSOCKETS) {
      // As a performance optimisation you can set a system property to disable websockets altogether which avoids
      // some casting and a header check
    } else {
      holder = new HandlerHolder<>(holder.context, new HttpHandlers(
        new WebSocketRequestHandler(metrics, holder.handler),
        holder.handler.wsHandler,
        holder.handler.connectionHandler,
        holder.handler.exceptionHandler));
      initializeWebsocketExtensions (pipeline);
    }
    Http1xServerHandler handler = new Http1xServerHandler(sslHelper, options, serverOrigin, holder, metrics);
    handler.addHandler(conn -> {
      connectionMap.put(pipeline.channel(), conn);
    });
    handler.removeHandler(conn -> {
      connectionMap.remove(pipeline.channel());
    });
    pipeline.addLast("handler", handler);

  }

  private void initializeWebsocketExtensions(ChannelPipeline pipeline) {
    ArrayList<WebSocketServerExtensionHandshaker> extensionHandshakers = new ArrayList<>();
    if (options.perFrameWebsocketCompressionSupported()) {
      extensionHandshakers.add(new DeflateFrameServerExtensionHandshaker(options.websocketCompressionLevel()));
    }
    if (options.perMessageWebsocketCompressionSupported()) {
      extensionHandshakers.add(new PerMessageDeflateServerExtensionHandshaker(options.websocketCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        options.getWebsocketAllowServerNoContext(), options.getWebsocketPreferredClientNoContext()));
    }
    if (!extensionHandshakers.isEmpty()) {
      WebSocketServerExtensionHandler extensionHandler = new WebSocketServerExtensionHandler(
        extensionHandshakers.toArray(new WebSocketServerExtensionHandshaker[extensionHandshakers.size()]));
      pipeline.addLast("websocketExtensionHandler", extensionHandler);
    }
  }

  private void handleHttp1(Channel ch) {
    HandlerHolder<HttpHandlers> holder = httpHandlerMgr.chooseHandler(ch.eventLoop());
    if (holder == null) {
      sendServiceUnavailable(ch);
      return;
    }
    configureHttp1(ch.pipeline(), holder);
  }

  private void sendServiceUnavailable(Channel ch) {
    ch.writeAndFlush(
      Unpooled.copiedBuffer("HTTP/1.1 503 Service Unavailable\r\n" +
        "Content-Length:0\r\n" +
        "\r\n", StandardCharsets.ISO_8859_1))
      .addListener(ChannelFutureListener.CLOSE);
  }

  private void handleHttp2(Channel ch) {
    HandlerHolder<HttpHandlers> holder = httpHandlerMgr.chooseHandler(ch.eventLoop());
    if (holder == null) {
      ch.close();
      return;
    }
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = buildHttp2ConnectionHandler(holder);
    ch.pipeline().addLast("handler", handler);
    configureHttp2(ch.pipeline());
  }

  void configureHttp2(ChannelPipeline pipeline) {
    if (options.getIdleTimeout() > 0) {
      pipeline.addBefore("handler", "idle", new IdleStateHandler(0, 0, options.getIdleTimeout(), options.getIdleTimeoutUnit()));
    }
  }

  @Override
  public void close() {
    close(null);
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
      }
    }
    if (creatingContext != null) {
      creatingContext.removeCloseHook(this);
    }
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

  private void applyConnectionOptions(ServerBootstrap bootstrap) {
    vertx.transport().configure(options, bootstrap);
  }


  private void addHandlers(HttpServerImpl server, ContextInternal context) {
    server.httpHandlerMgr.addHandler(
      new HttpHandlers(
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
  private class HttpStreamHandler<C extends ReadStream<Buffer>> implements ReadStream<C> {

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
