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
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.streams.ReadStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

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

  private final HttpServerOptions options;
  private final VertxInternal vertx;
  private final SSLHelper sslHelper;
  private final ContextImpl creatingContext;
  private final Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap<>();
  private final Map<Channel, Http2ServerConnection> connectionMap2 = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private final HandlerManager<HttpHandler> reqHandlerManager = new HandlerManager<>(availableWorkers);
  private final HandlerManager<Handler<ServerWebSocket>> wsHandlerManager = new HandlerManager<>(availableWorkers);
  private final ServerWebSocketStreamImpl wsStream = new ServerWebSocketStreamImpl();
  private final HttpServerRequestStreamImpl requestStream = new HttpServerRequestStreamImpl();
  private Handler<HttpConnection> connectionHandler;
  private final String subProtocols;
  private String serverOrigin;

  private ChannelGroup serverChannelGroup;
  private volatile boolean listening;
  private ChannelFuture bindFuture;
  private ServerID id;
  private HttpServerImpl actualServer;
  private ContextImpl listenContext;
  private HttpServerMetrics metrics;

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
    this.sslHelper = new SSLHelper(options, KeyStoreHelper.create(vertx, options.getKeyCertOptions()), KeyStoreHelper.create(vertx, options.getTrustOptions()));
    this.subProtocols = options.getWebsocketSubProtocols();
  }

  @Override
  public synchronized HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    requestStream.handler(handler);
    return this;
  }

  @Override
  public HttpServerRequestStream requestStream() {
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
  public ServerWebSocketStream websocketStream() {
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
      id = new ServerID(port, host);
      HttpServerImpl shared = vertx.sharedHttpServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(vertx.getAcceptorEventLoopGroup(), availableWorkers);
        bootstrap.channelFactory(new VertxNioServerChannelFactory());
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
                pipeline.addLast("ssl", sslHelper.createSslHandler(vertx));
                if (options.isUseAlpn()) {
                  pipeline.addLast("alpn", new ApplicationProtocolNegotiationHandler("http/1.1") {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                      if (protocol.equals("http/1.1")) {
                        configureHttp1(pipeline);
                      } else {
                        HandlerHolder<HttpHandler> holder = reqHandlerManager.chooseHandler(ch.eventLoop());
                        VertxHttp2ConnectionHandler<Http2ServerConnection> handler = createHttp2Handler(holder, ch);
                        configureHttp2(pipeline, handler);
                        if (holder.handler.connectionHandler != null) {
                          holder.context.executeFromIO(() -> {
                            holder.handler.connectionHandler.handle(handler.connection);
                          });
                        }
                      }
                    }
                  });
                } else {
                  configureHttp1(pipeline);
                }
              } else {
                configureHttp1(pipeline);
              }
            }
        });

        addHandlers(this, listenContext);
        try {
          bindFuture = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
          Channel serverChannel = bindFuture.channel();
          serverChannelGroup.add(serverChannel);
          bindFuture.addListener(channelFuture -> {
              if (!channelFuture.isSuccess()) {
                vertx.sharedHttpServers().remove(id);
              } else {
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
        addHandlers(actualServer, listenContext);
        metrics = vertx.metricsSPI().createMetrics(this, new SocketAddressImpl(port, host), options);
      }
      actualServer.bindFuture.addListener(future -> {
        if (listenHandler != null) {
          final AsyncResult<HttpServer> res;
          if (future.isSuccess()) {
            res = Future.succeededFuture(HttpServerImpl.this);
          } else {
            res = Future.failedFuture(future.cause());
            listening = false;
          }
          listenContext.runOnContext((v) -> listenHandler.handle(res));
        } else if (!future.isSuccess()) {
          listening  = false;
          // No handler - log so user can see failure
          log.error(future.cause());
        }
      });
    }
    return this;
  }

  private VertxHttp2ConnectionHandler<Http2ServerConnection> createHttp2Handler(HandlerHolder<HttpHandler> holder, Channel ch) {
    return new VertxHttp2ConnectionHandlerBuilder<Http2ServerConnection>()
        .connectionMap(connectionMap2)
        .server(true)
        .useCompression(options.isCompressionSupported())
        .initialSettings(options.getInitialSettings())
        .connectionFactory(connHandler -> new Http2ServerConnection(ch, holder.context, serverOrigin, connHandler, options, holder.handler.requesthHandler, metrics))
        .build();
  }

  private void configureHttp1(ChannelPipeline pipeline) {
    if (USE_FLASH_POLICY_HANDLER) {
      pipeline.addLast("flashpolicy", new FlashPolicyHandler());
    }
    pipeline.addLast("httpDecoder", new HttpRequestDecoder(options.getMaxInitialLineLength()
        , options.getMaxHeaderSize(), options.getMaxChunkSize(), false));
    pipeline.addLast("httpEncoder", new VertxHttpResponseEncoder());
    if (options.isCompressionSupported()) {
      pipeline.addLast("deflater", new HttpChunkContentCompressor());
    }
    if (sslHelper.isSSL() || options.isCompressionSupported()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
    pipeline.addLast("handler", new ServerHandler());
  }

  public void configureHttp2(ChannelPipeline pipeline, VertxHttp2ConnectionHandler<Http2ServerConnection> handler) {
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
    pipeline.addLast("handler", handler);
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
      done = new AsyncResultHandler<Void>() {
        @Override
        public void handle(AsyncResult<Void> event) {
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

  SSLHelper getSslHelper() {
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

  private void executeCloseDone(final ContextImpl closeContext, final Handler<AsyncResult<Void>> done, final Exception e) {
    if (done != null) {
      closeContext.runOnContext((v) -> done.handle(Future.failedFuture(e)));
    }
  }

  public class ServerHandler extends VertxHttpHandler<ServerConnection> {
    private boolean closeFrameSent;

    public ServerHandler() {
      super(HttpServerImpl.this.connectionMap);
    }

    FullHttpRequest wsRequest;

    @Override
    protected void doMessageReceived(ServerConnection conn, ChannelHandlerContext ctx, Object msg) throws Exception {

      Channel ch = ctx.channel();

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
        connectionMap.put(ch, conn);
        reqHandler.context.executeFromIO(() -> {
          conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
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
            connectRunnable, options.getMaxWebsocketFrameSize());
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
          options.getMaxWebsocketFrameSize());
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
  private class HttpStreamHandler<R extends ReadStream<C>, C extends ReadStream<?>> implements ReadStream<C> {

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
    public R handler(Handler<C> handler) {
      synchronized (HttpServerImpl.this) {
        if (listening) {
          throw new IllegalStateException("Please set handler before server is listening");
        }
        this.handler = handler;
        return (R) this;
      }
    }

    @Override
    public R pause() {
      synchronized (HttpServerImpl.this) {
        if (!paused) {
          paused = true;
        }
        return (R) this;
      }
    }

    @Override
    public R resume() {
      synchronized (HttpServerImpl.this) {
        if (paused) {
          paused = false;
        }
        return (R) this;
      }
    }

    @Override
    public R endHandler(Handler<Void> endHandler) {
      synchronized (HttpServerImpl.this) {
        this.endHandler = endHandler;
        return (R) this;
      }
    }

    @Override
    public R exceptionHandler(Handler<Throwable> handler) {
      // Should we use it in the server close exception handler ?
      return (R) this;
    }
  }

  class HttpServerRequestStreamImpl extends HttpStreamHandler<HttpServerRequestStream, HttpServerRequest> implements HttpServerRequestStream {
  }

  class ServerWebSocketStreamImpl extends HttpStreamHandler<ServerWebSocketStream, ServerWebSocket> implements ServerWebSocketStream {
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
}
