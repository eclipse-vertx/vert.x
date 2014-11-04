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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerRequestStream;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketStream;
import io.vertx.core.http.impl.cgbystrom.FlashPolicyHandler;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.metrics.spi.HttpServerMetrics;
import io.vertx.core.net.impl.HandlerHolder;
import io.vertx.core.net.impl.HandlerManager;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.net.impl.VertxEventLoopGroup;
import io.vertx.core.streams.ReadStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

//import io.netty.handler.codec.http.

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerImpl implements HttpServer, Closeable {

  private static final Logger log = LoggerFactory.getLogger(HttpServerImpl.class);

  private final HttpServerOptions options;
  private final VertxInternal vertx;
  private final SSLHelper sslHelper;
  private final HttpServerMetrics metrics;
  private final ContextImpl creatingContext;
  private final Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap<>();
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private ChannelGroup serverChannelGroup;
  private ServerWebSocketStreamImpl wsStream;
  private HttpServerRequestStreamImpl requestStream;
  private boolean listening;
  private String serverOrigin;
  private String subProtocols;
  private ChannelFuture bindFuture;
  private ServerID id;
  private HttpServerImpl actualServer;
  private HandlerManager<HttpServerRequest> reqHandlerManager = new HandlerManager<>(availableWorkers);
  private HandlerManager<ServerWebSocket> wsHandlerManager = new HandlerManager<>(availableWorkers);
  private ContextImpl listenContext;

  public HttpServerImpl(VertxInternal vertx, HttpServerOptions options) {
    this.options = new HttpServerOptions(options);
    this.vertx = vertx;
    this.creatingContext = vertx.getContext();
    if (creatingContext != null) {
      if (creatingContext.isMultiThreaded()) {
        throw new IllegalStateException("Cannot use HttpServer in a multi-threaded worker verticle");
      }
      creatingContext.addCloseHook(this);
    }
    this.sslHelper = new SSLHelper(options, KeyStoreHelper.create(vertx, options.getKeyStoreOptions()), KeyStoreHelper.create(vertx, options.getTrustStoreOptions()));
    this.wsStream = new ServerWebSocketStreamImpl();
    this.requestStream = new HttpServerRequestStreamImpl();
    this.subProtocols = options.getWebsocketSubProtocols();
    this.metrics = vertx.metricsSPI().createMetrics(this, options);
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
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
    return requestStream.handler;
  }

  @Override
  public Handler<ServerWebSocket> websocketHandler() {
    return wsStream.handler;
  }

  @Override
  public ServerWebSocketStream websocketStream() {
    return wsStream;
  }

  public HttpServer listen() {
    return listen(null);
  }

  public synchronized  HttpServer listen(final Handler<AsyncResult<HttpServer>> listenHandler) {
    if (requestStream.handler == null && wsStream.handler == null) {
      throw new IllegalStateException("Set request or websocket handler first");
    }
    if (listening) {
      throw new IllegalStateException("Already listening");
    }
    listenContext = vertx.getOrCreateContext();
    listening = true;

    synchronized (vertx.sharedHttpServers()) {
      id = new ServerID(options.getPort(), options.getHost());

      serverOrigin = (options.isSsl() ? "https" : "http") + "://" + options.getHost() + ":" + options.getPort();

      HttpServerImpl shared = vertx.sharedHttpServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(availableWorkers);
        bootstrap.channel(NioServerSocketChannel.class);
        applyConnectionOptions(bootstrap);
        sslHelper.validate(vertx);
        bootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
              if (requestStream.paused || wsStream.paused) {
                ch.close();
                return;
              }
              ChannelPipeline pipeline = ch.pipeline();
              if (sslHelper.isSSL()) {
                pipeline.addLast("ssl", sslHelper.createSslHandler(vertx, false));
              }
              pipeline.addLast("flashpolicy", new FlashPolicyHandler());
              pipeline.addLast("httpDecoder", new HttpRequestDecoder(4096, 8192, 8192, false));
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
        });

        addHandlers(this, listenContext);
        try {
          bindFuture = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(options.getHost()), options.getPort()));
          Channel serverChannel = bindFuture.channel();
          serverChannelGroup.add(serverChannel);
          bindFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
              if (!channelFuture.isSuccess()) {
                vertx.sharedHttpServers().remove(id);
              } else {
                metrics.listening(new SocketAddressImpl(options.getPort(), options.getHost()));
              }
            }
          });
        } catch (final Throwable t) {
          // Make sure we send the exception back through the handler (if any)
          if (listenHandler != null) {
            vertx.runOnContext(v -> listenHandler.handle(Future.completedFuture(t)));
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
        metrics.listening(new SocketAddressImpl(options.getPort(), options.getHost()));
      }
      actualServer.bindFuture.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
          if (listenHandler != null) {
            final AsyncResult<HttpServer> res;
            if (future.isSuccess()) {
              res = Future.completedFuture(HttpServerImpl.this);
            } else {
              res = Future.completedFuture(future.cause());
              listening = false;
            }
            // FIXME - workaround for https://github.com/netty/netty/issues/2586
            // If listen already succeeded on a different event loop, and then addListener is called again
            // on the completed future from a different event loop then the handler will be called on the original
            // event loop not on the when that called addListener.
            // To reproduce set the boolean parameter on execute (below) to true.
            // Then run Httptest.testTwoServersSameAddressDifferentContext()
            try {
              listenContext.execute(() -> {
                listenHandler.handle(res);
              }, false);
            } catch (Exception e) {
              e.printStackTrace();
            }
          } else if (!future.isSuccess()) {
            listening  = false;
            // No handler - log so user can see failure
            log.error(future.cause());
          }
        }
      });
    }
    return this;
  }

  @Override
  public void close() {
    close(null);
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> done) {
    if (wsStream.endHandler != null || requestStream.endHandler != null) {
      Handler<AsyncResult<Void>> next = done;
      done = new AsyncResultHandler<Void>() {
        @Override
        public void handle(AsyncResult<Void> event) {
          if (event.succeeded()) {
            if (wsStream.endHandler != null) {
              wsStream.endHandler.handle(event.result());
            }
            if (requestStream.endHandler != null) {
              requestStream.endHandler.handle(event.result());
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

        if (requestStream.handler != null) {
          actualServer.reqHandlerManager.removeHandler(requestStream, listenContext);
        }
        if (wsStream.handler != null) {
          actualServer.wsHandlerManager.removeHandler(wsStream, listenContext);
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
    requestStream.handler = null;
    wsStream.handler = null;
    if (creatingContext != null) {
      creatingContext.removeCloseHook(this);
    }
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

    bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    if (options.getTrafficClass() != -1) {
      bootstrap.childOption(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);

    bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    bootstrap.option(ChannelOption.SO_BACKLOG, options.getAcceptBacklog());
  }


  private void addHandlers(HttpServerImpl server, ContextImpl context) {
    if (requestStream.handler != null) {
      server.reqHandlerManager.addHandler(requestStream, context);
    }
    if (wsStream.handler != null) {
      server.wsHandlerManager.addHandler(wsStream, context);
    }
  }

  private void actualClose(final ContextImpl closeContext, final Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedHttpServers().remove(id);
    }

    for (ServerConnection conn : connectionMap.values()) {
      conn.close();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    vertx.setContext(closeContext);

    metrics.close();

    ChannelGroupFuture fut = serverChannelGroup.close();
    fut.addListener(cgf -> executeCloseDone(closeContext, done, fut.cause()));
  }

  private void executeCloseDone(final ContextImpl closeContext, final Handler<AsyncResult<Void>> done, final Exception e) {
    if (done != null) {
      closeContext.execute(() -> done.handle(Future.completedFuture(e)), false);
    }
  }

  private void checkListening() {
    if (listening) {
      throw new IllegalStateException("Can't set property when server is listening");
    }
  }

  public class ServerHandler extends VertxHttpHandler<ServerConnection> {
    private boolean closeFrameSent;

    public ServerHandler() {
      super(vertx, HttpServerImpl.this.connectionMap);
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

    FullHttpRequest wsRequest;

    @Override
    protected void doMessageReceived(ServerConnection conn, ChannelHandlerContext ctx, Object msg) throws Exception {
      Channel ch = ctx.channel();

      if (msg instanceof HttpRequest) {
        final HttpRequest request = (HttpRequest) msg;

        if (log.isTraceEnabled()) log.trace("Server received request: " + request.getUri());

        if (HttpHeaders.is100ContinueExpected(request)) {
          ch.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
        }

        if (wsHandlerManager.hasHandlers() && request.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, io.vertx.core.http.HttpHeaders.WEBSOCKET, true)) {
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
            HandlerHolder<HttpServerRequest> reqHandler = reqHandlerManager.chooseHandler(ch.eventLoop());
            if (reqHandler != null) {
              // We need to set the context manually as this is executed directly, not via context.execute()
              vertx.setContext(reqHandler.context);
              conn = new ServerConnection(vertx, HttpServerImpl.this, ch, reqHandler.context, serverOrigin, null, metrics);
              conn.requestHandler(reqHandler.handler);
              connectionMap.put(ch, conn);
              conn.handleMessage(msg);
            }
          } else {
            conn.handleMessage(msg);
          }
        }
      } else if (msg instanceof WebSocketFrameInternal) {
        //Websocket frame
        WebSocketFrameInternal wsFrame = (WebSocketFrameInternal)msg;
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
    }

    private String getWebSocketLocation(ChannelPipeline pipeline, FullHttpRequest req) throws Exception {
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

    private void handshake(final FullHttpRequest request, final Channel ch, ChannelHandlerContext ctx) throws Exception {
      final WebSocketServerHandshaker shake;
      WebSocketServerHandshakerFactory factory =
          new WebSocketServerHandshakerFactory(getWebSocketLocation(ch.pipeline(), request), subProtocols, false,
                                               options.getMaxWebsocketFrameSize());
      shake = factory.newHandshaker(request);

      if (shake == null) {
        log.error("Unrecognised websockets handshake");
        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
        return;
      }
      HandlerHolder<ServerWebSocket> firstHandler = null;
      HandlerHolder<ServerWebSocket> wsHandler = wsHandlerManager.chooseHandler(ch.eventLoop());

      while (true) {
        if (wsHandler == null) {
          break;
        }
        // Set context manually
        vertx.setContext(wsHandler.context);
        if (firstHandler == wsHandler) {
          break;
        }
        URI theURI;
        try {
          theURI = new URI(request.getUri());
        } catch (URISyntaxException e2) {
          throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
        }

        final ServerConnection wsConn = new ServerConnection(vertx, HttpServerImpl.this, ch, wsHandler.context,
                                                             serverOrigin, shake, metrics);
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

        final ServerWebSocketImpl ws = new ServerWebSocketImpl(vertx, theURI.toString(), theURI.getPath(),
            theURI.getQuery(), new HeadersAdaptor(request.headers()), wsConn, shake.version() != WebSocketVersion.V00,
            connectRunnable);
        wsConn.handleWebsocketConnect(ws);
        if (ws.isRejected()) {
          if (firstHandler == null) {
            firstHandler = wsHandler;
          }
        } else {
          ChannelHandler handler = ctx.pipeline().get(HttpChunkContentCompressor.class);
          if (handler != null) {
            // remove compressor as its not needed anymore once connection was upgraded to websockets
            ctx.pipeline().remove(handler);
          }
          ws.connectNow();
          return;
        }
      }
      ch.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, BAD_GATEWAY));
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

  class HttpStreamHandler<R extends ReadStream<C>, C extends ReadStream<?>> implements Handler<C>, ReadStream<C> {

    protected Handler<C> handler;
    protected boolean paused;
    Handler<Void> endHandler;

    @Override
    public R handler(Handler<C> handler) {
      if (listening) {
        throw new IllegalStateException("Please set handler before server is listening");
      }
      this.handler = handler;
      return (R) this;
    }

    @Override
    public void handle(C conn) {
      handler.handle(conn);
    }

    @Override
    public R pause() {
      if (!paused) {
        paused = true;
      }
      return (R) this;
    }

    @Override
    public R resume() {
      if (paused) {
        paused = false;
      }
      return (R) this;
    }

    @Override
    public R endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return (R) this;
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
}
