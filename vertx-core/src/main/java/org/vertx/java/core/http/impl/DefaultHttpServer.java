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

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.Handshake;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.http.impl.ws.hybi00.Handshake00;
import org.vertx.java.core.http.impl.ws.hybi08.Handshake08;
import org.vertx.java.core.http.impl.ws.hybi17.HandshakeRFC6455;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.HandlerHolder;
import org.vertx.java.core.net.impl.HandlerManager;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.net.impl.TCPSSLHelper;
import org.vertx.java.core.net.impl.VertxWorkerPool;

import javax.net.ssl.SSLEngine;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServer implements HttpServer {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServer.class);

  private final VertxInternal vertx;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private final Context ctx;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap<>();
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private String serverOrigin;

  private ServerID id;
  private DefaultHttpServer actualServer;
  private VertxWorkerPool availableWorkers = new VertxWorkerPool();
  private HandlerManager<HttpServerRequest> reqHandlerManager = new HandlerManager<>(availableWorkers);
  private HandlerManager<ServerWebSocket> wsHandlerManager = new HandlerManager<>(availableWorkers);

  public DefaultHttpServer(VertxInternal vertx) {
    this.vertx = vertx;
    ctx = vertx.getOrAssignContext();
    if (vertx.isWorker()) {
      throw new IllegalStateException("Cannot be used in a worker application");
    }
    ctx.putCloseHook(this, new Runnable() {
      public void run() {
        close();
      }
    });
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
      serverOrigin = (isSSL() ? "https" : "http") + "://" + host + ":" + port;
      id = new ServerID(port, host);
      DefaultHttpServer shared = vertx.sharedHttpServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels");
        ChannelFactory factory =
            new NioServerSocketChannelFactory(
                vertx.getAcceptorPool(),
                availableWorkers);
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setOptions(tcpHelper.generateConnectionOptions(true));

        tcpHelper.checkSSL();

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
          public ChannelPipeline getPipeline() {
            ChannelPipeline pipeline = Channels.pipeline();

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

            pipeline.addLast("decoder", new HttpRequestDecoder());
            pipeline.addLast("encoder", new HttpResponseEncoder());

            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
            pipeline.addLast("handler", new ServerHandler());
            return pipeline;
          }
        });

        try {
          Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
          serverChannelGroup.add(serverChannel);
        } catch (UnknownHostException e) {
          log.error("Failed to bind", e);
        }
        vertx.sharedHttpServers().put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = shared;
      }
      if (requestHandler != null) {
        actualServer.reqHandlerManager.addHandler(requestHandler, ctx);
      }
      if (wsHandler != null) {
        actualServer.wsHandlerManager.addHandler(wsHandler, ctx);
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
        executeCloseDone(ctx, done);
      }
      return;
    }
    listening = false;

    synchronized (vertx.sharedHttpServers()) {

      if (actualServer != null) {

        if (requestHandler != null) {
          actualServer.reqHandlerManager.removeHandler(requestHandler, ctx);
        }
        if (wsHandler != null) {
          actualServer.wsHandlerManager.removeHandler(wsHandler, ctx);
        }

        if (actualServer.reqHandlerManager.hasHandlers() || actualServer.wsHandlerManager.hasHandlers()) {
          // The actual server still has handlers so we don't actually close it
          if (done != null) {
            executeCloseDone(ctx, done);
          }
        } else {
          // No Handlers left so close the actual server
          // The done handler needs to be executed on the context that calls close, NOT the context
          // of the actual server
          actualServer.actualClose(ctx, done);
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

  public HttpServer setSoLinger(boolean linger) {
    tcpHelper.setSoLinger(linger);
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

  public Boolean isSoLinger() {
    return tcpHelper.isSoLinger();
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

  private void actualClose(final Context closeContext, final Handler<Void> done) {
    if (id != null) {
      vertx.sharedHttpServers().remove(id);
    }

    for (ServerConnection conn : connectionMap.values()) {
      conn.internalClose();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    Context.setContext(closeContext);

    ChannelGroupFuture fut = serverChannelGroup.close();
    if (done != null) {
      fut.addListener(new ChannelGroupFutureListener() {
        public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
          executeCloseDone(closeContext, done);
        }
      });
    }
  }

  private void executeCloseDone(final Context closeContext, final Handler<Void> done) {
    closeContext.execute(new Runnable() {
      public void run() {
        done.handle(null);
      }
    });
  }

  private static final AtomicInteger count = new AtomicInteger(0);

  public class ServerHandler extends SimpleChannelUpstreamHandler {

    private void sendError(String err, HttpResponseStatus status, Channel ch) {
      HttpResponse resp = new DefaultHttpResponse(HTTP_1_1, status);
      resp.setChunked(false);
      if (status.getCode() == METHOD_NOT_ALLOWED.getCode()) {
        // SockJS requires this
        resp.setHeader("allow", "GET");
      }
      if (err != null) {
        ChannelBuffer buff = ChannelBuffers.copiedBuffer(err.getBytes(Charset.forName("UTF-8")));
        resp.setHeader("Content-Length", err.length());
        resp.setContent(buff);
      } else {
        resp.setHeader(HttpHeaders.Names.CONTENT_LENGTH, "0");
      }

      ch.write(resp);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      Object msg = e.getMessage();
      ServerConnection conn = connectionMap.get(ch);
      if (msg instanceof HttpRequest) {

        final HttpRequest request = (HttpRequest) msg;

        if (log.isTraceEnabled()) log.trace("Server received request: " + request.getUri());

        if (HttpHeaders.is100ContinueExpected(request)) {
          ch.write(new DefaultHttpResponse(HTTP_1_1, CONTINUE));
        }

        if (WEBSOCKET.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.UPGRADE))) {
          // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
          // it doesn't send a normal 'Connection: Upgrade' header. Instead it
          // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
          String connectionHeader = request.getHeader(CONNECTION);
          if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
            sendError("\"Connection\" must be \"Upgrade\".", BAD_REQUEST, ch);
            return;
          }

          if (request.getMethod() != HttpMethod.GET) {
            sendError(null, METHOD_NOT_ALLOWED, ch);
            return;
          }

          final Handshake shake;
          if (HandshakeRFC6455.matches(request)) {
            shake = new HandshakeRFC6455();
          } else if (Handshake08.matches(request)) {
            shake = new Handshake08();
          } else if (Handshake00.matches(request)) {
            shake = new Handshake00();
          } else {
            log.error("Unrecognised websockets handshake");
            ch.write(new DefaultHttpResponse(HTTP_1_1, NOT_FOUND));
            return;
          }

          HandlerHolder<ServerWebSocket> firstHandler = null;

          while (true) {
            HandlerHolder<ServerWebSocket> wsHandler = wsHandlerManager.chooseHandler(ch.getWorker());
            if (wsHandler == null || firstHandler == wsHandler) {
              break;
            }

            URI theURI;
            try {
              theURI = new URI(request.getUri());
            } catch (URISyntaxException e2) {
              throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
            }

            final ServerConnection wsConn = new ServerConnection(vertx, ch, wsHandler.context);
            wsConn.wsHandler(wsHandler.handler);
            Runnable connectRunnable = new Runnable() {
              public void run() {
                connectionMap.put(ch, wsConn);
                try {
                  HttpResponse resp = shake.generateResponse(request, serverOrigin);
                  ChannelPipeline p = ch.getPipeline();
                  p.replace("decoder", "wsdecoder", shake.getDecoder());
                  ch.write(resp);
                  p.replace("encoder", "wsencoder", shake.getEncoder(true));
                } catch (Exception e) {
                  log.error("Failed to generate shake response", e);
                }
              }
            };
            DefaultWebSocket ws = new DefaultWebSocket(vertx, theURI.getPath(), wsConn, connectRunnable);
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
          ch.write(new DefaultHttpResponse(HTTP_1_1, NOT_FOUND));
        } else {
          //HTTP request
          if (conn == null) {
            HandlerHolder<HttpServerRequest> reqHandler = reqHandlerManager.chooseHandler(ch.getWorker());
            if (reqHandler != null) {
              conn = new ServerConnection(vertx, ch, reqHandler.context);
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
      } else if (msg instanceof HttpChunk) {
        if (conn != null) {
          conn.handleMessage(msg);
        }
      } else {
        throw new IllegalStateException("Invalid message " + msg);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ServerConnection conn = connectionMap.get(ch);
      final Throwable t = e.getCause();
      ch.close();
      if (conn != null && t instanceof Exception) {
        conn.getContext().execute(new Runnable() {
          public void run() {
            conn.handleException((Exception) t);
          }
        });
      } else {
        // Ignore - any exceptions not associated with any sock (e.g. failure in ssl handshake) will
        // be communicated explicitly
      }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      //NOOP
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ServerConnection conn = connectionMap.remove(ch);
      if (conn != null) {
        conn.getContext().execute(new Runnable() {
          public void run() {
            conn.handleClosed();
          }
        });
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ServerConnection conn = connectionMap.get(ch);
      ChannelState state = e.getState();
      if (state == ChannelState.INTEREST_OPS) {
        conn.getContext().execute(new Runnable() {
          public void run() {
            conn.handleInterestedOpsChanged();
          }
        });
      }
    }
  }
}
