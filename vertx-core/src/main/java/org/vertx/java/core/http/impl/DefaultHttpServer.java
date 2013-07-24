/**
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
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
import org.vertx.java.core.http.impl.cgbystrom.FlashPolicyHandler;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.*;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.*;

import javax.net.ssl.SSLEngine;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServer implements HttpServer, Closeable {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServer.class);
  private static final ExceptionDispatchHandler EXCEPTION_DISPATCH_HANDLER = new ExceptionDispatchHandler();

  final VertxInternal vertx;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private final DefaultContext actualCtx;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  final Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap<>();
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private String serverOrigin;

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
              pipeline.addLast("exceptionDispatcher", EXCEPTION_DISPATCH_HANDLER);
              if (tcpHelper.isSSL()) {
                SSLEngine engine = tcpHelper.getSSLContext().createSSLEngine();
                engine.setUseClientMode(false);
                switch (tcpHelper.getClientAuth()) {
                  case REQUEST: {
                    engine.setWantClientAuth(true);
                    break;
                  }
                  case REQUIRED: {
                    engine.setNeedClientAuth(true);
                    break;
                  }
                  case NONE: {
                    engine.setNeedClientAuth(false);
                    break;
                  }
                }
                pipeline.addLast("ssl", new SslHandler(engine));
              }
              pipeline.addLast("flashpolicy", new FlashPolicyHandler());
              pipeline.addLast("httpDecoder", new HttpRequestDecoder());
              pipeline.addLast("httpEncoder", new HttpResponseEncoder());
              if (tcpHelper.isSSL()) {
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
          t.printStackTrace();
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
    public ServerHandler() {
      super(vertx, DefaultHttpServer.this.connectionMap);
    }

    private void sendError(String err, HttpResponseStatus status, Channel ch) {
      FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status);
      if (status.code() == METHOD_NOT_ALLOWED.code()) {
        // SockJS requires this
        resp.headers().set("allow", "GET");
      }
      if (err != null) {
        resp.content().writeBytes(err.getBytes(CharsetUtil.UTF_8));
        resp.headers().set("Content-Length", err.length());
      } else {
        resp.headers().set(HttpHeaders.Names.CONTENT_LENGTH, "0");
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

        if (wsHandlerManager.hasHandlers() && WEBSOCKET.equalsIgnoreCase(request.headers().get(HttpHeaders.Names.UPGRADE))) {
          // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
          // it doesn't send a normal 'Connection: Upgrade' header. Instead it
          // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
          String connectionHeader = request.headers().get(CONNECTION);
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
      } else if (msg instanceof WebSocketFrame) {
        //Websocket frame
        WebSocketFrame wsFrame = (WebSocketFrame)msg;
        switch (wsFrame.getType()) {
          case BINARY:
          case TEXT:
            if (conn != null) {
              conn.handleMessage(msg);
            }
            break;
          case CLOSE:
            //Echo back close frame
            ch.writeAndFlush(new DefaultWebSocketFrame(WebSocketFrame.FrameType.CLOSE));
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
      return prefix + req.headers().get(HOST) + new URI(req.getUri()).getPath();
    }

    private void handshake(final FullHttpRequest request, final Channel ch, ChannelHandlerContext ctx) throws Exception {
      final WebSocketServerHandshaker shake;
      WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(getWebSocketLocation(ch.pipeline(), request), null, false);
      shake = factory.newHandshaker(request);

      if (shake == null) {
        log.error("Unrecognised websockets handshake");
        WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ch);
        return;
      }
      //ch.pipeline().addBefore(ctx.name(), "websocketConverter", WebSocketConvertHandler.INSTANCE);

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
            } catch (Exception e) {
              log.error("Failed to generate shake response", e);
            }
          }
        };

        final DefaultServerWebSocket ws = new DefaultServerWebSocket(vertx, theURI.getPath(),
            theURI.getQuery(), new HttpHeadersAdapter(request.headers()), wsConn, connectRunnable);
        wsConn.handleWebsocketConnect(ws);
        if (ws.isRejected()) {
          if (firstHandler == null) {
            firstHandler = wsHandler;
          }
        } else {
          ws.connectNow();
          return;
        }
      }
      ch.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, BAD_GATEWAY));
    }
  }
}
