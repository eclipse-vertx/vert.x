/*
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.impl.cgbystrom.FlashPolicyHandler;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketConvertHandler;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.EventLoopContext;
import org.vertx.java.core.impl.ExceptionDispatchHandler;
import org.vertx.java.core.impl.FlowControlHandler;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.*;

import javax.net.ssl.SSLEngine;
import java.net.*;
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
public class DefaultHttpServer implements HttpServer {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServer.class);
    private static final ExceptionDispatchHandler EXCEPTION_DISPATCH_HANDLER = new ExceptionDispatchHandler();

  private final VertxInternal vertx;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private final Context actualCtx;
  private final EventLoopContext eventLoopContext;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap<>();
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private String serverOrigin;

  private ServerID id;
  private DefaultHttpServer actualServer;
  private VertxEventLoopGroup availableWorkers = new VertxEventLoopGroup();
  private HandlerManager<HttpServerRequest> reqHandlerManager = new HandlerManager<>(availableWorkers);
  private HandlerManager<ServerWebSocket> wsHandlerManager = new HandlerManager<>(availableWorkers);

  public DefaultHttpServer(VertxInternal vertx) {
    this.vertx = vertx;
    // This is kind of fiddly - this class might be used by a worker, in which case the context is not
    // an event loop context - but we need an event loop context so that netty can deliver any messages for the connection
    // Therefore, if the current context is not an event loop one, we need to create one and register that with the
    // handler manager when registering handlers
    // We then do a check when messages are delivered that we're on the right worker before delivering the message
    // All of this will be massively simplified in Netty 4.0 when the event loop becomes a first class citizen
    actualCtx = vertx.getOrAssignContext();
    actualCtx.putCloseHook(this, new Runnable() {
      public void run() {
        close();
      }
    });
    if (actualCtx instanceof EventLoopContext) {
      eventLoopContext = (EventLoopContext)actualCtx;
    } else {
      eventLoopContext = vertx.createEventLoopContext();
    }
    tcpHelper.setReuseAddress(true);
  }

  public HttpServer requestHandler(Handler<HttpServerRequest> requestHandler) {
    if (listening) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    this.requestHandler = requestHandler;
    return this;
  }

  public Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  public HttpServer websocketHandler(Handler<ServerWebSocket> wsHandler) {
    if (listening) {
      throw new IllegalStateException("Please set handler before server is listening");
    }
    this.wsHandler = wsHandler;
    return this;
  }

  public Handler<ServerWebSocket> websocketHandler() {
    return wsHandler;
  }

  public HttpServer listen(int port) {
    return listen(port, "0.0.0.0");
  }

  public HttpServer listen(int port, String host) {

    if (requestHandler == null && wsHandler == null) {
      throw new IllegalStateException("Set request or websocket handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }

    synchronized (vertx.sharedHttpServers()) {
      id = new ServerID(port, host);

      serverOrigin = (isSSL() ? "https" : "http") + "://" + host + ":" + port;

      DefaultHttpServer shared = vertx.sharedHttpServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels");
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(vertx.getServerAcceptorPool(), availableWorkers);
        bootstrap.channel(NioServerSocketChannel.class);
        tcpHelper.applyConnectionOptions(bootstrap);
        tcpHelper.checkSSL(vertx);

        bootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
              ChannelPipeline pipeline = ch.pipeline();
              pipeline.addLast("exceptionDispatcher", EXCEPTION_DISPATCH_HANDLER);
              pipeline.addLast("flowControl", new FlowControlHandler());
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

              pipeline.addLast("codec", new HttpServerCodec());
              pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
              pipeline.addLast("handler", new ServerHandler());
            }
        });

        try {
          Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port)).syncUninterruptibly().channel();
          serverChannelGroup.add(serverChannel);
        } catch (ChannelException | UnknownHostException e) {
          throw new IllegalArgumentException(e.getMessage());
        }
        vertx.sharedHttpServers().put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = shared;
      }
      if (requestHandler != null) {
        // Share the event loop thread to also serve the HttpServer's network traffic.
        actualServer.reqHandlerManager.addHandler(requestHandler, eventLoopContext);
      }
      if (wsHandler != null) {
        // Share the event loop thread to also serve the HttpServer's network traffic.
        actualServer.wsHandlerManager.addHandler(wsHandler, eventLoopContext);
      }
    }
    listening = true;
    return this;
  }

  public void close() {
    close(null);
  }

  public void close(final Handler<Void> done) {
    if (!listening) {
      if (done != null) {
        executeCloseDone(actualCtx, done);
      }
      return;
    }
    listening = false;

    synchronized (vertx.sharedHttpServers()) {

      if (actualServer != null) {

        if (requestHandler != null) {
          actualServer.reqHandlerManager.removeHandler(requestHandler, eventLoopContext);
        }
        if (wsHandler != null) {
          actualServer.wsHandlerManager.removeHandler(wsHandler, eventLoopContext);
        }

        if (actualServer.reqHandlerManager.hasHandlers() || actualServer.wsHandlerManager.hasHandlers()) {
          // The actual server still has handlers so we don't actually close it
          if (done != null) {
            executeCloseDone(actualCtx, done);
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

  }

  public HttpServer setSSL(boolean ssl) {
    tcpHelper.setSSL(ssl);
    return this;
  }

  public HttpServer setKeyStorePath(String path) {
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  public HttpServer setKeyStorePassword(String pwd) {
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  public HttpServer setTrustStorePath(String path) {
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  public HttpServer setTrustStorePassword(String pwd) {
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  public HttpServer setClientAuthRequired(boolean required) {
    tcpHelper.setClientAuthRequired(required);
    return this;
  }

  public HttpServer setTCPNoDelay(boolean tcpNoDelay) {
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  public HttpServer setSendBufferSize(int size) {
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  public HttpServer setReceiveBufferSize(int size) {
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }
  public HttpServer setTCPKeepAlive(boolean keepAlive) {
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  public HttpServer setReuseAddress(boolean reuse) {
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  public HttpServer setSoLinger(int linger) {
    if (linger < 0) {
      tcpHelper.setSoLinger(null);
    } else {
      tcpHelper.setSoLinger(linger);
    }
    return this;
  }

  public HttpServer setTrafficClass(int trafficClass) {
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  public HttpServer setAcceptBacklog(int backlog) {
    tcpHelper.setAcceptBacklog(backlog);
    return this;
  }

  public Boolean isTCPNoDelay() {
    return tcpHelper.isTCPNoDelay();
  }

  public Integer getSendBufferSize() {
    return tcpHelper.getSendBufferSize();
  }

  public Integer getReceiveBufferSize() {
    return tcpHelper.getReceiveBufferSize();
  }

  public Boolean isTCPKeepAlive() {
    return tcpHelper.isTCPKeepAlive();
  }

  public Boolean isReuseAddress() {
    return tcpHelper.isReuseAddress();
  }

  public Integer getSoLinger() {
    return tcpHelper.getSoLinger();
  }

  public Integer getTrafficClass() {
    return tcpHelper.getTrafficClass();
  }

  public Integer getAcceptBacklog() {
    return tcpHelper.getAcceptBacklog();
  }

  public boolean isSSL() {
    return tcpHelper.isSSL();
  }

  public String getKeyStorePath() {
    return tcpHelper.getKeyStorePath();
  }

  public String getKeyStorePassword() {
    return tcpHelper.getKeyStorePassword();
  }

  public String getTrustStorePath() {
    return tcpHelper.getTrustStorePath();
  }

  public String getTrustStorePassword() {
    return tcpHelper.getTrustStorePassword();
  }

  public HttpServer setUsePooledBuffers(boolean pooledBuffers) {
    tcpHelper.setUsePooledBuffers(pooledBuffers);
    return this;
  }

  public boolean isUsePooledBuffers() {
    return tcpHelper.isUsePooledBuffers();
  }

  private void actualClose(final Context closeContext, final Handler<Void> done) {
    if (id != null) {
      vertx.sharedHttpServers().remove(id);
    }

    for (ServerConnection conn : connectionMap.values()) {
      conn.internalClose();
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

    executeCloseDone(closeContext, done);
  }

  private void executeCloseDone(final Context closeContext, final Handler<Void> done) {
    if (done != null) {
      closeContext.execute(new Runnable() {
        public void run() {
          done.handle(null);
        }
      });
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
        resp.data().writeBytes(err.getBytes(CharsetUtil.UTF_8));
        resp.headers().set("Content-Length", err.length());
      } else {
        resp.headers().set(HttpHeaders.Names.CONTENT_LENGTH, "0");
      }

      ch.write(resp);
    }

    FullHttpRequest wsRequest;

    @Override
    protected void doMessageReceived(ServerConnection conn, ChannelHandlerContext ctx, Object msg) throws Exception {
      Channel ch = ctx.channel();

      if (msg instanceof HttpRequest) {
        final HttpRequest request = (HttpRequest) msg;

        if (log.isTraceEnabled()) log.trace("Server received request: " + request.getUri());

        if (HttpHeaders.is100ContinueExpected(request)) {
          ch.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
        }

        if (WEBSOCKET.equalsIgnoreCase(request.headers().get(HttpHeaders.Names.UPGRADE))) {
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
              conn = new ServerConnection(vertx, ch, reqHandler.context, serverOrigin);
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
            ch.write(new DefaultWebSocketFrame(WebSocketFrame.FrameType.CLOSE));
        }
      } else if (msg instanceof HttpContent) {
        if (wsRequest != null) {
          wsRequest.data().writeBytes(((HttpContent) msg).data());
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
      ch.pipeline().addBefore(ctx.name(), "websocketConverter", WebSocketConvertHandler.INSTANCE);

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

        final ServerConnection wsConn = new ServerConnection(vertx, ch, wsHandler.context, serverOrigin);
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

        final DefaultWebSocket ws = new DefaultWebSocket(vertx, theURI.getPath(), wsConn, connectRunnable);
        wsConn.handleWebsocketConnect(ws);
        if (ws.rejected) {
          if (firstHandler == null) {
            firstHandler = wsHandler;
          }
        } else {
          ws.connectNow();
          return;
        }
      }
      ch.write(new DefaultFullHttpResponse(HTTP_1_1, BAD_GATEWAY));
    }
  }
}
