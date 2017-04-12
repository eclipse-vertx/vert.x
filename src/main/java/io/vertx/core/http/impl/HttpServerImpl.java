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
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.AsyncResolveConnectHelper;
import io.vertx.core.net.impl.HandlerHolder;
import io.vertx.core.net.impl.HandlerManager;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.net.impl.VertxEventLoopGroup;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.streams.ReadStream;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl implements HttpServer, Closeable, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);
  private static final String FLASH_POLICY_HANDLER_PROP_NAME = "vertx.flashPolicyHandler";
  private static final boolean USE_FLASH_POLICY_HANDLER = Boolean.getBoolean(FLASH_POLICY_HANDLER_PROP_NAME);
  private static final String DISABLE_WEBSOCKETS_PROP_NAME = "vertx.disableWebsockets";
  private static final boolean DISABLE_WEBSOCKETS = Boolean.getBoolean(DISABLE_WEBSOCKETS_PROP_NAME);
  private static final String DISABLE_H2C_PROP_NAME = "vertx.disableH2c";
  private final boolean DISABLE_HC2 = Boolean.getBoolean(DISABLE_H2C_PROP_NAME);
  private static final String[] H2C_HANDLERS_TO_REMOVE = { "idle", "flashpolicy", "deflater", "chunkwriter" };
  private static final byte[] HTTP_2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();

  private final HttpServerOptions options;
  private final VertxInternal vertx;
  private final SSLHelper sslHelper;
  private final ContextImpl creatingContext;
  private final Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap<>();
  private final Map<Channel, Http2ServerConnection> connectionMap2 = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<HttpHandler> reqHandlerManager = new HandlerManager<>(availableWorkers);
  private final HandlerManager<Handler<ServerWebSocket>> wsHandlerManager = new HandlerManager<>(availableWorkers);
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
  private ContextImpl listenContext;
  private HttpServerMetrics metrics;
  private boolean logEnabled;
  private Handler<Throwable> connectionExceptionHandler;

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
    connectionExceptionHandler = t -> {log.trace("Connection failure", t);};
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
        bootstrap.channel(NioServerSocketChannel.class);
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
                pipeline.addLast("ssl", new SslHandler(sslHelper.createEngine(vertx)));
                if (options.isUseAlpn()) {
                  pipeline.addLast("alpn", new ApplicationProtocolNegotiationHandler("http/1.1") {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                      if (protocol.equals("http/1.1")) {
                        configureHttp1(pipeline);
                      } else {
                        handleHttp2(ch);
                      }
                    }
                  });
                } else {
                  configureHttp1(pipeline);
                }
              } else {
                if (DISABLE_HC2) {
                  configureHttp1(pipeline);
                } else {
                  pipeline.addLast(new Http1xOrHttp2Handler());
                }
              }
            }
        });

        addHandlers(this, listenContext);
        try {
          bindFuture = AsyncResolveConnectHelper.doBind(vertx, port, host, bootstrap);
          bindFuture.addListener(res -> {
            if (res.failed()) {
              vertx.sharedHttpServers().remove(id);
            } else {
              Channel serverChannel = res.result();
              HttpServerImpl.this.actualPort = ((InetSocketAddress)serverChannel.localAddress()).getPort();
              serverChannelGroup.add(serverChannel);
              metrics = vertx.metricsSPI().createMetrics(this, new SocketAddressImpl(port, host), options);
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
        metrics = vertx.metricsSPI().createMetrics(this, new SocketAddressImpl(port, host), options);
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

  // Visible for testing
  public HttpServerImpl setConnectionExceptionHandler(Handler<Throwable> connectionExceptionHandler) {
    Objects.requireNonNull(connectionExceptionHandler, "connectionExceptionHandler");
    this.connectionExceptionHandler = connectionExceptionHandler;
    return this;
  }

  private VertxHttp2ConnectionHandler<Http2ServerConnection> createHttp2Handler(HandlerHolder<HttpHandler> holder, Channel ch) {
    return new VertxHttp2ConnectionHandlerBuilder<Http2ServerConnection>()
        .connectionMap(connectionMap2)
        .server(true)
        .useCompression(options.isCompressionSupported())
        .useDecompression(options.isDecompressionSupported())
        .compressionLevel(options.getCompressionLevel())
        .initialSettings(options.getInitialSettings())
        .connectionFactory(connHandler -> {
          Http2ServerConnection conn = new Http2ServerConnection(ch, holder.context, serverOrigin, connHandler, options, holder.handler.requesthHandler, metrics);
          conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
          return conn;
        })
        .logEnabled(logEnabled)
        .build();
  }

  private void configureHttp1(ChannelPipeline pipeline) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    if (USE_FLASH_POLICY_HANDLER) {
      pipeline.addLast("flashpolicy", new FlashPolicyHandler());
    }
    pipeline.addLast("httpDecoder", new HttpRequestDecoder(options.getMaxInitialLineLength()
        , options.getMaxHeaderSize(), options.getMaxChunkSize(), false));
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
    pipeline.addLast("handler", new ServerHandler(pipeline.channel()));
  }

  public void handleHttp2(Channel ch) {
    HandlerHolder<HttpHandler> holder = reqHandlerManager.chooseHandler(ch.eventLoop());
    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = createHttp2Handler(holder, ch);
    configureHttp2(ch.pipeline(), handler);
    if (holder.handler.connectionHandler != null) {
      holder.context.executeFromIO(() -> {
        holder.handler.connectionHandler.handle(handler.connection);
      });
    }
  }

  public void configureHttp2(ChannelPipeline pipeline, VertxHttp2ConnectionHandler<Http2ServerConnection> handler) {
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
    pipeline.addLast("handler", handler);
    if (options.getHttp2ConnectionWindowSize() > 0) {
      handler.connection.setWindowSize(options.getHttp2ConnectionWindowSize());
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

        if (requestStream.handler() != null) {
          actualServer.reqHandlerManager.removeHandler(new HttpHandler(requestStream.handler(), connectionHandler), listenContext);
        }
        if (wsStream.handler() != null) {
          actualServer.wsHandlerManager.removeHandler(wsStream.handler(), listenContext);
        }

        if (actualServer.reqHandlerManager.hasHandlers() || actualServer.wsHandlerManager.hasHandlers()) {
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
    return metrics != null && metrics.isEnabled();
  }

  public SSLHelper getSslHelper() {
    return sslHelper;
  }

  void removeChannel(Channel channel) {
    connectionMap.remove(channel);
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


  private void addHandlers(HttpServerImpl server, ContextImpl context) {
    if (requestStream.handler() != null) {
      server.reqHandlerManager.addHandler(new HttpHandler(requestStream.handler(), connectionHandler), context);
    }
    if (wsStream.handler() != null) {
      server.wsHandlerManager.addHandler(wsStream.handler(), context);
    }
  }

  private void actualClose(final ContextImpl closeContext, final Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedHttpServers().remove(id);
    }

    ContextImpl currCon = vertx.getContext();

    for (ServerConnection conn : connectionMap.values()) {
      conn.close();
    }
    for (Http2ServerConnection conn : connectionMap2.values()) {
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

  public class ServerHandler extends VertxHttpHandler<ServerConnection> {

    private boolean closeFrameSent;

    public ServerHandler(Channel ch) {
      super(HttpServerImpl.this.connectionMap, ch);
    }

    FullHttpRequest wsRequest;

    @Override
    protected void doMessageReceived(ServerConnection conn, ChannelHandlerContext ctx, Object msg) throws Exception {

      // As a performance optimisation you can set a system property to disable websockets altogether which avoids
      // some casting and a header check
      if (!DISABLE_WEBSOCKETS) {

        if (msg instanceof HttpRequest) {
          final HttpRequest request = (HttpRequest) msg;

          if (log.isTraceEnabled()) log.trace("Server received request: " + request.getUri());

          if (request.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, io.vertx.core.http.HttpHeaders.WEBSOCKET, true)) {
            // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
            // it doesn't send a normal 'Connection: Upgrade' header. Instead it
            // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
            String connectionHeader = request.headers().get(io.vertx.core.http.HttpHeaders.CONNECTION);
            if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
              sendError("\"Connection\" must be \"Upgrade\".", BAD_REQUEST, ch);
              return;
            }

            if (request.getMethod() != HttpMethod.GET) {
              sendError(null, METHOD_NOT_ALLOWED, ch);
              return;
            }

            if (wsRequest == null) {
              if (request instanceof FullHttpRequest) {
                handshake((FullHttpRequest) request, ch, ctx);
              } else {
                wsRequest = new DefaultFullHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri());
                wsRequest.headers().set(request.headers());
              }
            }
          } else {
            //HTTP request
            if (conn == null) {
              createConnAndHandle(ctx, ch, msg, null);
            } else {
              conn.handleMessage(msg);
            }
          }
        } else if (msg instanceof WebSocketFrameInternal) {
          //Websocket frame
          WebSocketFrameInternal wsFrame = (WebSocketFrameInternal) msg;
          switch (wsFrame.type()) {
            case BINARY:
            case CONTINUATION:
            case TEXT:
              if (conn != null) {
                conn.handleMessage(msg);
              }
              break;
            case PING:
              // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
              ch.writeAndFlush(new WebSocketFrameImpl(FrameType.PONG, wsFrame.getBinaryData()));
              break;
            case PONG:
              // Just ignore it
              break;
            case CLOSE:
              if (!closeFrameSent) {
                // Echo back close frame and close the connection once it was written.
                // This is specified in the WebSockets RFC 6455 Section  5.4.1
                ch.writeAndFlush(wsFrame).addListener(ChannelFutureListener.CLOSE);
                closeFrameSent = true;
              }
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
              handshake(req, ch, ctx);
              return;
            }
          }
          if (conn != null) {
            conn.handleMessage(msg);
          }
        } else {
          throw new IllegalStateException("Invalid message " + msg);
        }
      } else {
        if (conn == null) {



          createConnAndHandle(ctx, ch, msg, null);
        } else {
          conn.handleMessage(msg);
        }
      }
    }


    private void createConnAndHandle(ChannelHandlerContext ctx, Channel ch, Object msg, WebSocketServerHandshaker shake) {
      HandlerHolder<HttpHandler> reqHandler = reqHandlerManager.chooseHandler(ch.eventLoop());
      if (reqHandler != null) {
        if (!DISABLE_HC2 && msg instanceof HttpRequest) {
          HttpRequest request = (HttpRequest) msg;
          if (request.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, true)) {
            if (reqHandler.context.isEventLoopContext()) {
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
                Http2Settings settings = null;
                String settingsHeader = request.headers().get(Http2CodecUtil.HTTP_UPGRADE_SETTINGS_HEADER);
                if (settingsHeader != null) {
                  settings = HttpUtils.decodeSettings(settingsHeader);
                }
                if (settings != null) {
                  ChannelPipeline pipeline = ch.pipeline();
                  DefaultFullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, SWITCHING_PROTOCOLS, Unpooled.EMPTY_BUFFER, false);
                  res.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
                  res.headers().add(HttpHeaderNames.UPGRADE, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME);
                  res.headers().add(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                  ctx.writeAndFlush(res).addListener(v -> {
                    // Clean more pipeline ?
                    pipeline.remove("handler");
                    pipeline.remove("httpDecoder");
                    pipeline.remove("httpEncoder");
                  });
                  for (String name : H2C_HANDLERS_TO_REMOVE) {
                    if (pipeline.get(name) != null) {
                      pipeline.remove(name);
                    }
                  }
                  try {
                    VertxHttp2ConnectionHandler<Http2ServerConnection> handler = createHttp2Handler(reqHandler, ch);
                    handler.onHttpServerUpgrade(settings);
                    configureHttp2(pipeline, handler);
                    return;
                  } catch (Http2Exception ignore) {
                  }
                }
              }
            }
            DefaultFullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, SWITCHING_PROTOCOLS, Unpooled.EMPTY_BUFFER, false);
            res.setStatus(BAD_REQUEST);
            ctx.writeAndFlush(res);
            return;
          }
        }

        // Put in the connection map before executeFromIO
        ServerConnection conn = new ServerConnection(vertx, HttpServerImpl.this, ch, reqHandler.context, serverOrigin, shake, metrics);
        conn.requestHandler(reqHandler.handler.requesthHandler);
        this.conn = conn;
        connectionMap.put(ch, conn);
        reqHandler.context.executeFromIO(() -> {
          conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
          Handler<HttpConnection> connHandler = reqHandler.handler.connectionHandler;
          if (connHandler != null) {
            connHandler.handle(conn);
          }
          conn.handleMessage(msg);
        });
      }
    }

    private void handshake(FullHttpRequest request, Channel ch, ChannelHandlerContext ctx) throws Exception {

      WebSocketServerHandshaker shake = createHandshaker(ch, request);
      if (shake == null) {
        return;
      }
      HandlerHolder<Handler<ServerWebSocket>> wsHandler = wsHandlerManager.chooseHandler(ch.eventLoop());

      if (wsHandler == null) {
        createConnAndHandle(ctx, ch, request, shake);
      } else {

        wsHandler.context.executeFromIO(() -> {
          URI theURI;
          try {
            theURI = new URI(request.getUri());
          } catch (URISyntaxException e2) {
            throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
          }

          ServerConnection wsConn = new ServerConnection(vertx, HttpServerImpl.this, ch, wsHandler.context,
            serverOrigin, shake, metrics);
          wsConn.metric(metrics.connected(wsConn.remoteAddress(), wsConn.remoteName()));
          wsConn.wsHandler(wsHandler.handler);

          Runnable connectRunnable = () -> {
            VertxHttpHandler<ServerConnection> handler = ch.pipeline().get(VertxHttpHandler.class);
            handler.conn = wsConn;
            connectionMap.put(ch, wsConn);
            try {
              shake.handshake(ch, request);
            } catch (WebSocketHandshakeException e) {
              wsConn.handleException(e);
            } catch (Exception e) {
              log.error("Failed to generate shake response", e);
            }
          };

          ServerWebSocketImpl ws = new ServerWebSocketImpl(vertx, theURI.toString(), theURI.getPath(),
            theURI.getQuery(), new HeadersAdaptor(request.headers()), wsConn, shake.version() != WebSocketVersion.V00,
            connectRunnable, options.getMaxWebsocketFrameSize(), options().getMaxWebsocketMessageSize());
          ws.setMetric(metrics.connected(wsConn.metric(), ws));
          wsConn.handleWebsocketConnect(ws);
          if (!ws.isRejected()) {
            ChannelHandler handler = ctx.pipeline().get(HttpChunkContentCompressor.class);
            if (handler != null) {
              // remove compressor as its not needed anymore once connection was upgraded to websockets
              ctx.pipeline().remove(handler);
            }
            ws.connectNow();
          } else {
            ch.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, BAD_GATEWAY));
          }
        });
      }
    }
  }

  WebSocketServerHandshaker createHandshaker(Channel ch, HttpRequest request)  {
    // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
    // it doesn't send a normal 'Connection: Upgrade' header. Instead it
    // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
    String connectionHeader = request.headers().get(io.vertx.core.http.HttpHeaders.CONNECTION);
    if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
      sendError("\"Connection\" must be \"Upgrade\".", BAD_REQUEST, ch);
      return null;
    }

    if (request.getMethod() != HttpMethod.GET) {
      sendError(null, METHOD_NOT_ALLOWED, ch);
      return null;
    }

    try {

      WebSocketServerHandshakerFactory factory =
        new WebSocketServerHandshakerFactory(getWebSocketLocation(ch.pipeline(), request), subProtocols, false,
          options.getMaxWebsocketFrameSize(),options.isAcceptUnmaskedFrames());
      WebSocketServerHandshaker shake = factory.newHandshaker(request);

      if (shake == null) {
        log.error("Unrecognised websockets handshake");
        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
      }

      return shake;
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  private void sendError(CharSequence err, HttpResponseStatus status, Channel ch) {
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

  private String getWebSocketLocation(ChannelPipeline pipeline, HttpRequest req) throws Exception {
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

  Map<Channel, ServerConnection> connectionMap() {
    return connectionMap;
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

  class HttpHandler {
    final Handler<HttpServerRequest> requesthHandler;
    final Handler<HttpConnection> connectionHandler;
    public HttpHandler(Handler<HttpServerRequest> requesthHandler, Handler<HttpConnection> connectionHandler) {
      this.requesthHandler = requesthHandler;
      this.connectionHandler = connectionHandler;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HttpHandler that = (HttpHandler) o;

      if (!requesthHandler.equals(that.requesthHandler)) return false;
      if (connectionHandler != null ? !connectionHandler.equals(that.connectionHandler) : that.connectionHandler != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = reqHandlerManager.hashCode();
      if (connectionHandler != null) {
        result = 31 * result + connectionHandler.hashCode();
      }
      return result;
    }
  }

  /**
   * Handler that detects whether the HTTP/2 connection preface or just process the request
   * with the HTTP 1.x pipeline to support H2C with prior knowledge, i.e a client that connects
   * and uses HTTP/2 in clear text directly without an HTTP upgrade.
   */
  private class Http1xOrHttp2Handler extends ChannelInboundHandlerAdapter {

    private int index = 0;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      ByteBuf buf = (ByteBuf) msg;
      int len = buf.readableBytes();
      for (int i = index;i < len;i++) {
        if (i == HTTP_2_PREFACE.length) {
          // H2C
          http2(ctx, buf);
          break;
        } else {
          if (buf.getByte(i) != HTTP_2_PREFACE[i]) {
            http1(ctx, buf);
            break;
          }
        }
      }
    }

    private void http2(ChannelHandlerContext ctx, ByteBuf buf) {
      ByteBuf msg;
      if (index > 0) {
        msg = Unpooled.buffer(index + buf.readableBytes());
        msg.setBytes(0, HTTP_2_PREFACE, 0, index);
        msg.setBytes(index, buf);
        buf = msg;
      }
      handleHttp2(ctx.channel());
      ctx.fireChannelRead(buf);
      ctx.pipeline().remove(this);
    }

    private void http1(ChannelHandlerContext ctx, ByteBuf buf) {
      ByteBuf msg;
      if (index > 0) {
        msg = Unpooled.buffer(index + buf.readableBytes());
        msg.setBytes(0, HTTP_2_PREFACE, 0, index);
        msg.setBytes(index, buf);
        buf = msg;
      }
      configureHttp1(ctx.pipeline());
      ctx.fireChannelRead(buf);
      ctx.pipeline().remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      Channel channel = ctx.channel();
      channel.close();
      HandlerHolder<HttpHandler> reqHandler = reqHandlerManager.chooseHandler(channel.eventLoop());
      reqHandler.context.executeFromIO(() -> HttpServerImpl.this.connectionExceptionHandler.handle(cause));
  }
}
}
