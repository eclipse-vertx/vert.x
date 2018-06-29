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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl implements HttpServer, Closeable, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);
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
  private final ContextImpl creatingContext;
  private final Map<Channel, ConnectionBase> connectionMap = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<HttpHandlers> httpHandlerMgr = new HandlerManager<>(availableWorkers);
  private final HttpStreamHandler<ServerWebSocket> wsStream = new HttpStreamHandler<>();
  private final HttpStreamHandler<HttpServerRequest> requestStream = new HttpStreamHandler<>();
  private Handler<HttpConnection> connectionHandler;
  private String serverOrigin;

  private ChannelGroup serverChannelGroup;
  private volatile boolean listening;
  private AsyncResolveConnectHelper bindFuture;
  private ServerID id;
  private HttpServerImpl actualServer;
  private volatile int actualPort;
  private ContextImpl listenContext;
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

  public synchronized HttpServer listen(int port, String host, Handler<AsyncResult<HttpServer>> listenHandler) {
    if (requestStream.handler() == null && wsStream.handler() == null) {
      throw new IllegalStateException("Set request or websocket handler first");
    }
    if (listening) {
      throw new IllegalStateException("Already listening");
    }
    listenContext = vertx.getOrCreateContext();
    listening = true;
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
              if (requestStream.isPaused() || wsStream.isPaused()) {
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
                    handler.context.executeFromIO(() -> handler.handler.exceptionHandler.handle(future.cause()));
                  }
                });
              } else {
                if (DISABLE_H2C) {
                  handleHttp1(ch);
                } else {
                  IdleStateHandler idle;
                  if (options.getIdleTimeout() > 0) {
                    pipeline.addLast("idle", idle = new IdleStateHandler(0, 0, options.getIdleTimeout()));
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
                      handler.context.executeFromIO(() -> handler.handler.exceptionHandler.handle(cause));
                    }
                  });
                }
              }
            }
        });

        addHandlers(this, listenContext);
        try {
          bindFuture = AsyncResolveConnectHelper.doBind(vertx, SocketAddress.inetSocketAddress(port, host), bootstrap);
          bindFuture.addListener(res -> {
            if (res.failed()) {
              vertx.sharedHttpServers().remove(id);
            } else {
              Channel serverChannel = res.result();
              HttpServerImpl.this.actualPort = ((InetSocketAddress)serverChannel.localAddress()).getPort();
              serverChannelGroup.add(serverChannel);
              VertxMetrics metrics = vertx.metricsSPI();
              this.metrics = metrics != null ? metrics.createMetrics(this, new SocketAddressImpl(port, host), options) : null;
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
        this.metrics = metrics != null ? metrics.createMetrics(this, new SocketAddressImpl(port, host), options) : null;
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

  private VertxHttp2ConnectionHandler<Http2ServerConnection> buildHttp2ConnectionHandler(HandlerHolder<HttpHandlers> holder) {
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
        holder.context.executeFromIO(() -> {
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
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
    if (!DISABLE_H2C) {
      pipeline.addLast("h2c", new Http2UpgradeHandler());
    }
    Http1xServerHandler handler;
    if (DISABLE_WEBSOCKETS) {
      // As a performance optimisation you can set a system property to disable websockets altogether which avoids
      // some casting and a header check
      handler = new Http1xServerHandler(sslHelper, options, serverOrigin, holder, metrics);
    } else {
      handler = new ServerHandlerWithWebSockets(sslHelper, options, serverOrigin, holder, metrics);
    }
    handler.addHandler(conn -> {
      connectionMap.put(pipeline.channel(), conn);
    });
    handler.removeHandler(conn -> {
      connectionMap.remove(pipeline.channel());
    });
    pipeline.addLast("handler", handler);
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

  private void configureHttp2(ChannelPipeline pipeline) {
    if (options.getIdleTimeout() > 0) {
      pipeline.addBefore("handler", "idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
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

    ContextImpl context = vertx.getOrCreateContext();
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


  private void addHandlers(HttpServerImpl server, ContextImpl context) {
    server.httpHandlerMgr.addHandler(
      new HttpHandlers(
        requestStream.handler(),
        wsStream.handler(),
        connectionHandler,
        exceptionHandler == null ? DEFAULT_EXCEPTION_HANDLER : exceptionHandler)
      , context);
  }

  private void actualClose(final ContextImpl closeContext, final Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedHttpServers().remove(id);
    }

    ContextImpl currCon = vertx.getContext();

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

  private void executeCloseDone(final ContextImpl closeContext, final Handler<AsyncResult<Void>> done, final Exception e) {
    if (done != null) {
      Future<Void> fut = e != null ? Future.failedFuture(e) : Future.succeededFuture();
      closeContext.runOnContext((v) -> done.handle(fut));
    }
  }

  public class ServerHandlerWithWebSockets extends Http1xServerHandler {

    private boolean closeFrameSent;
    private FullHttpRequest wsRequest;
    private HttpResponseStatus handshakeErrorStatus;
    private String handshakeErrorMsg;

    public ServerHandlerWithWebSockets(SSLHelper sslHelper, HttpServerOptions options, String serverOrigin, HandlerHolder<HttpHandlers> holder, HttpServerMetrics metrics) {
      super(sslHelper, options, serverOrigin, holder, metrics);
    }

    @Override
    protected void handleMessage(Http1xServerConnection conn, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {
      Channel ch = chctx.channel();
      if (msg instanceof HttpRequest) {
        final HttpRequest request = (HttpRequest) msg;

        if (log.isTraceEnabled()) log.trace("Server received request: " + request.getUri());

        if (request.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, io.vertx.core.http.HttpHeaders.WEBSOCKET, true)) {

          // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
          // it doesn't send a normal 'Connection: Upgrade' header. Instead it
          // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
          String connectionHeader = request.headers().get(io.vertx.core.http.HttpHeaders.CONNECTION);
          if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
            handshakeErrorStatus = BAD_REQUEST;
            handshakeErrorMsg = "\"Connection\" must be \"Upgrade\".";
            return;
          }

          if (request.getMethod() != HttpMethod.GET) {
            handshakeErrorStatus = METHOD_NOT_ALLOWED;
            sendError(null, METHOD_NOT_ALLOWED, ch);
            return;
          }

          if (wsRequest == null) {
            if (request instanceof FullHttpRequest) {
              handshake(conn, (FullHttpRequest) request, ch, chctx);
            } else {
              wsRequest = new DefaultFullHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri());
              wsRequest.headers().set(request.headers());
            }
          }
        } else {
          //HTTP request
          conn.handleMessage(msg);
        }
      } else if (msg instanceof WebSocketFrameInternal) {
        //Websocket frame
        WebSocketFrameInternal wsFrame = (WebSocketFrameInternal) msg;
        switch (wsFrame.type()) {
          case BINARY:
          case CONTINUATION:
          case TEXT:
          case PONG:
            conn.handleMessage(msg);
            break;
          case PING:
            // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
            conn.channel().writeAndFlush(new PongWebSocketFrame(wsFrame.getBinaryData().copy()));
            break;
          case CLOSE:
            if (!closeFrameSent) {
              // Echo back close frame and close the connection once it was written.
              // This is specified in the WebSockets RFC 6455 Section  5.4.1
              CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(wsFrame.closeStatusCode(), wsFrame.closeReason());
              ch.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);
              closeFrameSent = true;
            }
            conn.handleMessage(msg);
            break;
          default:
            throw new IllegalStateException("Invalid type: " + wsFrame.type());
        }
      } else if (msg instanceof HttpContent) {
        if (wsRequest != null) {
          wsRequest.content().writeBytes(((HttpContent) msg).content());
          if (msg instanceof LastHttpContent) {
            FullHttpRequest req = wsRequest;
            wsRequest = null;
            handshake(conn, req, ch, chctx);
            return;
          }
        } else if (handshakeErrorStatus != null) {
          if (msg instanceof LastHttpContent) {
            sendError(handshakeErrorMsg, handshakeErrorStatus, ch);
            handshakeErrorMsg = null;
            handshakeErrorMsg = null;
          }
          return;
        }
        conn.handleMessage(msg);
      } else {
        throw new IllegalStateException("Invalid message " + msg);
      }
    }

    protected void handshake(Http1xServerConnection conn, FullHttpRequest request, Channel ch, ChannelHandlerContext ctx) throws Exception {

      WebSocketServerHandshaker shake = createHandshaker(conn, ch, request);
      if (shake == null) {
        return;
      }
      HandlerHolder<HttpHandlers> wsHandler = httpHandlerMgr.chooseHandler(ch.eventLoop());

      if (wsHandler == null || wsHandler.handler.wsHandler == null) {
        conn.handleMessage(request);
      } else {

        wsHandler.context.executeFromIO(() -> {
          URI theURI;
          try {
            theURI = new URI(request.getUri());
          } catch (URISyntaxException e2) {
            throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
          }

          if (metrics != null) {
            conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
          }
          conn.wsHandler(shake, wsHandler.handler);

          Supplier<String> connectRunnable = () -> {
            try {
              shake.handshake(ch, request);
              return shake.selectedSubprotocol();
            } catch (WebSocketHandshakeException e) {
              conn.handleException(e);
              return null;
            } catch (Exception e) {
              log.error("Failed to generate shake response", e);
              return null;
            }
          };

          ServerWebSocketImpl ws = new ServerWebSocketImpl(vertx, theURI.toString(), theURI.getPath(),
              theURI.getQuery(), new HeadersAdaptor(request.headers()), conn, shake.version() != WebSocketVersion.V00,
              connectRunnable, options.getMaxWebsocketFrameSize(), options().getMaxWebsocketMessageSize());
          if (METRICS_ENABLED && metrics != null) {
            ws.setMetric(metrics.connected(conn.metric(), ws));
          }
          conn.handleWebsocketConnect(ws);
          if (!ws.isRejected()) {
            ChannelHandler handler = ctx.pipeline().get(HttpChunkContentCompressor.class);
            if (handler != null) {
              // remove compressor as its not needed anymore once connection was upgraded to websockets
              ctx.pipeline().remove(handler);
            }
            ws.connectNow();
          } else {
            ch.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, ws.getRejectedStatus()));
          }
        });
      }
    }
  }

  static void sendError(CharSequence err, HttpResponseStatus status, Channel ch) {
    FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status);
    if (status.code() == METHOD_NOT_ALLOWED.code()) {
      // SockJS requires this
      resp.headers().set(io.vertx.core.http.HttpHeaders.ALLOW, io.vertx.core.http.HttpHeaders.GET);
    }
    if (err != null) {
      resp.content().writeBytes(err.toString().getBytes(CharsetUtil.UTF_8));
      HttpHeaders.setContentLength(resp, err.length());
    } else {
      HttpHeaders.setContentLength(resp, 0);
    }

    ch.writeAndFlush(resp);
  }

  static String getWebSocketLocation(ChannelPipeline pipeline, HttpRequest req) throws Exception {
    String prefix;
    if (pipeline.get(SslHandler.class) == null) {
      prefix = "ws://";
    } else {
      prefix = "wss://";
    }
    URI uri = new URI(req.getUri());
    String path = uri.getRawPath();
    String loc =  prefix + HttpHeaders.getHost(req) + path;
    String query = uri.getRawQuery();
    if (query != null) {
      loc += "?" + query;
    }
    return loc;
  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close();
    super.finalize();
  }

  HttpServerOptions options() {
    return options;
  }

  /*
    Needs to be protected using the HttpServerImpl monitor as that protects the listening variable
    In practice synchronized overhead should be close to zero assuming most access is from the same thread due
    to biased locks
  */
  private class HttpStreamHandler<C extends ReadStream<Buffer>> implements ReadStream<C> {

    private Handler<C> handler;
    private boolean paused;
    private Handler<Void> endHandler;

    Handler<C> handler() {
      synchronized (HttpServerImpl.this) {
        return handler;
      }
    }

    boolean isPaused() {
      synchronized (HttpServerImpl.this) {
        return paused;
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
        if (!paused) {
          paused = true;
        }
        return this;
      }
    }

    @Override
    public ReadStream resume() {
      synchronized (HttpServerImpl.this) {
        if (paused) {
          paused = false;
        }
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

  private class Http2UpgradeHandler extends ChannelInboundHandlerAdapter {

    private VertxHttp2ConnectionHandler<Http2ServerConnection> handler;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof HttpRequest) {
        HttpRequest request = (HttpRequest) msg;
        if (request.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, true)) {
          String connection = request.headers().get(io.vertx.core.http.HttpHeaders.CONNECTION);
          int found = 0;
          if (connection != null && connection.length() > 0) {
            StringBuilder buff = new StringBuilder();
            int pos = 0;
            int len = connection.length();
            while (pos < len) {
              char c = connection.charAt(pos++);
              if (c != ' ' && c != ',') {
                buff.append(Character.toLowerCase(c));
              }
              if (c == ',' || pos == len) {
                if (buff.indexOf("upgrade") == 0 && buff.length() == 7) {
                  found |= 1;
                } else if (buff.indexOf("http2-settings") == 0 && buff.length() == 14) {
                  found |= 2;
                }
                buff.setLength(0);
              }
            }
          }
          if (found == 3) {
            String settingsHeader = request.headers().get(Http2CodecUtil.HTTP_UPGRADE_SETTINGS_HEADER);
            if (settingsHeader != null) {
              Http2Settings settings = HttpUtils.decodeSettings(settingsHeader);
              if (settings != null) {
                HandlerHolder<HttpHandlers> reqHandler = httpHandlerMgr.chooseHandler(ctx.channel().eventLoop());
                if (reqHandler != null && reqHandler.context.isEventLoopContext()) {
                  ChannelPipeline pipeline = ctx.pipeline();
                  DefaultFullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, SWITCHING_PROTOCOLS, Unpooled.EMPTY_BUFFER, false);
                  res.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
                  res.headers().add(HttpHeaderNames.UPGRADE, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME);
                  res.headers().add(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                  ctx.writeAndFlush(res);
                  pipeline.remove("httpEncoder");
                  pipeline.remove("handler");
                  handler = buildHttp2ConnectionHandler(reqHandler);
                  pipeline.addLast("handler", handler);
                  handler.serverUpgrade(ctx, settings, request);
                  DefaultHttp2Headers headers = new DefaultHttp2Headers();
                  headers.method(request.method().name());
                  headers.path(request.uri());
                  headers.authority(request.headers().get("host"));
                  headers.scheme("http");
                  request.headers().remove("http2-settings");
                  request.headers().remove("host");
                  request.headers().forEach(header -> headers.set(header.getKey().toLowerCase(), header.getValue()));
                  ctx.fireChannelRead(new DefaultHttp2HeadersFrame(headers, false));
                } else {
                  log.warn("Cannot perform HTTP/2 upgrade in a worker verticle");
                }
              }
            }
          }
          if (handler == null) {
            DefaultFullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST, Unpooled.EMPTY_BUFFER, false);
            res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(res);
          }
        } else {
          ctx.fireChannelRead(msg);
          ctx.pipeline().remove(this);
        }
      } else {
        if (handler != null) {
          if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            ByteBuf buf = VertxHandler.safeBuffer(content.content(), ctx.alloc());
            boolean end = msg instanceof LastHttpContent;
            ctx.fireChannelRead(new DefaultHttp2DataFrame(buf, end, 0));
            if (end) {
              ChannelPipeline pipeline = ctx.pipeline();
              Iterator<Map.Entry<String, ChannelHandler>> iterator = pipeline.iterator();
              while (iterator.hasNext()) {
                Map.Entry<String, ChannelHandler> handler = iterator.next();
                if (handler.getValue() instanceof Http2ConnectionHandler) {
                  // Continue
                } else {
                  pipeline.remove(handler.getKey());
                }
              }
              configureHttp2(pipeline);
            }
          } else {
            // We might have left over buffer sent when removing the HTTP decoder that needs to be propagated to the HTTP handler
            super.channelRead(ctx, msg);
          }
        }
      }
    }
  }
}
