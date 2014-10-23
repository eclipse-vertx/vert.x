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

package org.vertx.java.core.http.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.WebSocketFrame;
import org.vertx.java.core.http.impl.cgbystrom.FlashPolicyHandler;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrameInternal;
import org.vertx.java.core.impl.Closeable;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.HandlerHolder;
import org.vertx.java.core.net.impl.HandlerManager;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.net.impl.TCPSSLHelper;
import org.vertx.java.core.net.impl.VertxEventLoopGroup;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServer implements HttpServer, Closeable {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServer.class);

  final VertxInternal vertx;
  final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private final DefaultContext actualCtx;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  final Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap<>();
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private String serverOrigin;
  private boolean compressionSupported;
  private int maxWebSocketFrameSize = 65536;
  private Set<String> webSocketSubProtocols = Collections.unmodifiableSet(Collections.<String>emptySet());

  private ChannelFuture bindFuture;
  private ServerID id;
  private DefaultHttpServer actualServer;
  private final VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private HandlerManager<HttpServerRequest> reqHandlerManager = new HandlerManager<>(availableWorkers);
  private HandlerManager<ServerWebSocket> wsHandlerManager = new HandlerManager<>(availableWorkers);

  public DefaultHttpServer(VertxInternal vertx) {
    this.vertx = vertx;
    actualCtx = vertx.getOrCreateContext();
    actualCtx.addCloseHook(this);
    tcpHelper.setReuseAddress(true);
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> requestHandler) {
    if (listening) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    this.requestHandler = requestHandler;
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  @Override
  public HttpServer websocketHandler(Handler<ServerWebSocket> wsHandler) {
    if (listening) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    this.wsHandler = wsHandler;
    return this;
  }

  @Override
  public Handler<ServerWebSocket> websocketHandler() {
    return wsHandler;
  }

  public HttpServer listen(int port) {
    listen(port, "0.0.0.0", null);
    return this;
  }

  public HttpServer listen(int port, String host) {
    listen(port, host, null);
    return this;
  }

  public HttpServer listen(int port, Handler<AsyncResult<HttpServer>> listenHandler) {
    listen(port, "0.0.0.0", listenHandler);
    return this;
  }

  public HttpServer listen(int port, String host, final Handler<AsyncResult<HttpServer>> listenHandler) {
    if (requestHandler == null && wsHandler == null) {
      throw new IllegalStateException("Set request or websocket handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;

    synchronized (vertx.sharedHttpServers()) {
      id = new ServerID(port, host);

      serverOrigin = (isSSL() ? "https" : "http") + "://" + host + ":" + port;

      DefaultHttpServer shared = vertx.sharedHttpServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels", GlobalEventExecutor.INSTANCE);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(availableWorkers);
        bootstrap.channel(NioServerSocketChannel.class);
        tcpHelper.applyConnectionOptions(bootstrap);
        tcpHelper.checkSSL(vertx);
        bootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
              ChannelPipeline pipeline = ch.pipeline();
              if (tcpHelper.isSSL()) {
                pipeline.addLast("ssl", tcpHelper.createSslHandler(vertx, false));
              }
              pipeline.addLast("flashpolicy", new FlashPolicyHandler());
              pipeline.addLast("httpDecoder", new HttpRequestDecoder(4096, 8192, 8192, false));
              pipeline.addLast("httpEncoder", new VertxHttpResponseEncoder());
              if (compressionSupported) {
                pipeline.addLast("deflater", new HttpChunkContentCompressor());
              }
              if (tcpHelper.isSSL() || compressionSupported) {
                // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
                pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
              }
              pipeline.addLast("handler", new ServerHandler());
            }
        });

        addHandlers(this);
        try {
          bindFuture = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
          Channel serverChannel = bindFuture.channel();
          serverChannelGroup.add(serverChannel);
          bindFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
              if (!channelFuture.isSuccess()) {
                vertx.sharedHttpServers().remove(id);
              }
            }
          });
        } catch (final Throwable t) {
          // Make sure we send the exception back through the handler (if any)
          if (listenHandler != null) {
            vertx.runOnContext(new VoidHandler() {
              @Override
              protected void handle() {
                listenHandler.handle(new DefaultFutureResult<HttpServer>(t));
              }
            });
          } else {
            // No handler - log so user can see failure
            actualCtx.reportException(t);
          }
          listening = false;
          return this;
        }
        vertx.sharedHttpServers().put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = shared;
        addHandlers(actualServer);
      }
      actualServer.bindFuture.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
          if (listenHandler != null) {
            final AsyncResult<HttpServer> res;
            if (future.isSuccess()) {
              res = new DefaultFutureResult<HttpServer>(DefaultHttpServer.this);
            } else {
              res = new DefaultFutureResult<>(future.cause());
              listening = false;
            }
            actualCtx.execute(future.channel().eventLoop(), new Runnable() {
              @Override
              public void run() {
                listenHandler.handle(res);
              }
            });
          } else if (!future.isSuccess()) {
            listening  = false;
            // No handler - log so user can see failure
            actualCtx.reportException(future.cause());
          }
        }
      });
    }
    return this;
  }

  private void addHandlers(DefaultHttpServer server) {
    if (requestHandler != null) {
      server.reqHandlerManager.addHandler(requestHandler, actualCtx);
    }
    if (wsHandler != null) {
      server.wsHandlerManager.addHandler(wsHandler, actualCtx);
    }
  }

  @Override
  public void close() {
    close(null);
  }

  @Override
  public void close(final Handler<AsyncResult<Void>> done) {
    if (!listening) {
      executeCloseDone(actualCtx, done, null);
      return;
    }
    listening = false;

    synchronized (vertx.sharedHttpServers()) {

      if (actualServer != null) {

        if (requestHandler != null) {
          actualServer.reqHandlerManager.removeHandler(requestHandler, actualCtx);
        }
        if (wsHandler != null) {
          actualServer.wsHandlerManager.removeHandler(wsHandler, actualCtx);
        }

        if (actualServer.reqHandlerManager.hasHandlers() || actualServer.wsHandlerManager.hasHandlers()) {
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
    requestHandler = null;
    wsHandler = null;
    actualCtx.removeCloseHook(this);
  }

  @Override
  public HttpServer setSSL(boolean ssl) {
    checkListening();
    tcpHelper.setSSL(ssl);
    return this;
  }

  @Override
  public HttpServer setSSLContext(SSLContext sslContext) {
    checkListening();
    tcpHelper.setExternalSSLContext(sslContext);
    return this;
  }

  @Override
  public HttpServer setKeyStorePath(String path) {
    checkListening();
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  @Override
  public HttpServer setKeyStorePassword(String pwd) {
    checkListening();
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  @Override
  public HttpServer setTrustStorePath(String path) {
    checkListening();
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  @Override
  public HttpServer setTrustStorePassword(String pwd) {
    checkListening();
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  @Override
  public HttpServer setClientAuthRequired(boolean required) {
    checkListening();
    tcpHelper.setClientAuthRequired(required);
    return this;
  }

  @Override
  public HttpServer setTCPNoDelay(boolean tcpNoDelay) {
    checkListening();
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public HttpServer setSendBufferSize(int size) {
    checkListening();
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  @Override
  public HttpServer setReceiveBufferSize(int size) {
    checkListening();
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  @Override
  public HttpServer setTCPKeepAlive(boolean keepAlive) {
    checkListening();
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  @Override
  public HttpServer setReuseAddress(boolean reuse) {
    checkListening();
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  @Override
  public HttpServer setSoLinger(int linger) {
    checkListening();
    tcpHelper.setSoLinger(linger);
    return this;
  }

  @Override
  public HttpServer setTrafficClass(int trafficClass) {
    checkListening();
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public HttpServer setAcceptBacklog(int backlog) {
    checkListening();
    tcpHelper.setAcceptBacklog(backlog);
    return this;
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
  public HttpServer setUsePooledBuffers(boolean pooledBuffers) {
    checkListening();
    tcpHelper.setUsePooledBuffers(pooledBuffers);
    return this;
  }

  @Override
  public boolean isUsePooledBuffers() {
    return tcpHelper.isUsePooledBuffers();
  }

  @Override
  public HttpServer setCompressionSupported(boolean compressionSupported) {
    checkListening();
    this.compressionSupported = compressionSupported;
    return this;
  }

  @Override
  public boolean isCompressionSupported() {
    return compressionSupported;
  }

  @Override
  public HttpServer setMaxWebSocketFrameSize(int maxSize) {
    maxWebSocketFrameSize = maxSize;
    return this;
  }

  @Override
  public int getMaxWebSocketFrameSize() {
    return maxWebSocketFrameSize;
  }

  @Override
  public HttpServer setWebSocketSubProtocols(String... subProtocols) {
    if (subProtocols == null || subProtocols.length == 0) {
      webSocketSubProtocols = Collections.unmodifiableSet(Collections.<String>emptySet());
    } else {
      webSocketSubProtocols = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(subProtocols)));
    }
    return this;
  }

  @Override
  public Set<String> getWebSocketSubProtocols() {
    return webSocketSubProtocols;
  }

  private void actualClose(final DefaultContext closeContext, final Handler<AsyncResult<Void>> done) {
    if (id != null) {
      vertx.sharedHttpServers().remove(id);
    }

    for (ServerConnection conn : connectionMap.values()) {
      conn.close();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    vertx.setContext(closeContext);

    final CountDownLatch latch = new CountDownLatch(1);

    ChannelGroupFuture fut = serverChannelGroup.close();
    fut.addListener(new ChannelGroupFutureListener() {
      public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
        latch.countDown();
      }
    });

    // Always sync
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
    }

    executeCloseDone(closeContext, done, fut.cause());
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

  public class ServerHandler extends VertxHttpHandler<ServerConnection> {
    private boolean closeFrameSent;

    public ServerHandler() {
      super(vertx, DefaultHttpServer.this.connectionMap);
    }

    private void sendError(CharSequence err, HttpResponseStatus status, Channel ch) {
      FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status);
      if (status.code() == METHOD_NOT_ALLOWED.code()) {
        // SockJS requires this
        resp.headers().set(org.vertx.java.core.http.HttpHeaders.ALLOW, org.vertx.java.core.http.HttpHeaders.GET);
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

        if (wsHandlerManager.hasHandlers() && request.headers().contains(org.vertx.java.core.http.HttpHeaders.UPGRADE, org.vertx.java.core.http.HttpHeaders.WEBSOCKET, true)) {
          // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
          // it doesn't send a normal 'Connection: Upgrade' header. Instead it
          // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
          String connectionHeader = request.headers().get(org.vertx.java.core.http.HttpHeaders.CONNECTION);
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
              conn = new ServerConnection(DefaultHttpServer.this, ch, reqHandler.context, serverOrigin);
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
            ch.writeAndFlush(new DefaultWebSocketFrame(WebSocketFrame.FrameType.PONG, wsFrame.getBinaryData()));
            break;
          case CLOSE:
            if (!closeFrameSent) {
              // Echo back close frame and close the connection once it was written.
              // This is specified in the WebSockets RFC 6455 Section  5.4.1
              ch.writeAndFlush(wsFrame).addListener(ChannelFutureListener.CLOSE);
              closeFrameSent = true;
            }
            break;
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
      String subProtocols = null;
      Set<String> webSocketSubProtocols = DefaultHttpServer.this.webSocketSubProtocols;
      if (!webSocketSubProtocols.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> protocols = webSocketSubProtocols.iterator();
        while(protocols.hasNext()) {
          sb.append(protocols.next());
          if (protocols.hasNext()) {
            sb.append(',');
          }
        }
        subProtocols = sb.toString();
      }
      WebSocketServerHandshakerFactory factory =
          new WebSocketServerHandshakerFactory(getWebSocketLocation(ch.pipeline(), request), subProtocols, false,
                                               maxWebSocketFrameSize);
      shake = factory.newHandshaker(request);

      if (shake == null) {
        log.error("Unrecognised websockets handshake");
        WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ch);
        return;
      }
      HandlerHolder<ServerWebSocket> firstHandler = null;
      HandlerHolder<ServerWebSocket> wsHandler = wsHandlerManager.chooseHandler(ch.eventLoop());
      while (true) {
        if (wsHandler == null || firstHandler == wsHandler) {
          break;
        }

        URI theURI;
        try {
          theURI = new URI(request.getUri());
        } catch (URISyntaxException e2) {
          throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
        }

        final ServerConnection wsConn = new ServerConnection(DefaultHttpServer.this, ch, wsHandler.context, serverOrigin);
        wsConn.wsHandler(wsHandler.handler);

        Runnable connectRunnable = new Runnable() {
          public void run() {
            connectionMap.put(ch, wsConn);
            try {
              shake.handshake(ch, request);
            } catch (WebSocketHandshakeException e) {
              wsConn.handleException(e);
            } catch (Exception e) {
              log.error("Failed to generate shake response", e);
            }
          }
        };

        final DefaultServerWebSocket ws = new DefaultServerWebSocket(vertx, theURI.toString(), theURI.getPath(),
            theURI.getQuery(), new HttpHeadersAdapter(request.headers()), wsConn, connectRunnable);
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
}
