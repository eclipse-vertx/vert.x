/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http;

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
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.http.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.ws.Handshake;
import org.vertx.java.core.http.ws.WebSocketFrame;
import org.vertx.java.core.http.ws.hybi00.Handshake00;
import org.vertx.java.core.http.ws.hybi08.Handshake08;
import org.vertx.java.core.http.ws.hybi17.Handshake17;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.HandlerHolder;
import org.vertx.java.core.net.HandlerManager;
import org.vertx.java.core.net.NetServerBase;
import org.vertx.java.core.net.NetServerWorkerPool;
import org.vertx.java.core.net.ServerID;

import javax.net.ssl.SSLEngine;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * <p>An HTTP server.</p>
 * <p>The server supports both HTTP requests and HTML5 websockets and passes these to the user via the appropriate handlers.</p>
 * <p>An {@code HttpServer} instance can only be used from the event loop that created it.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServer extends NetServerBase {

  private static final Logger log = Logger.getLogger(HttpServer.class);

  //For debug only
  public static int numServers() {
    return servers.size();
  }

  private static final Map<ServerID, HttpServer> servers = new HashMap<>();

  private Handler<HttpServerRequest> requestHandler;
  private WebSocketHandler wsHandler;
  private Map<Channel, ServerConnection> connectionMap = new ConcurrentHashMap();
  private ChannelGroup serverChannelGroup;
  private boolean listening;

  private ServerID id;
  private HttpServer actualServer;
  private NetServerWorkerPool availableWorkers = new NetServerWorkerPool();
  private HandlerManager<HttpServerRequest> reqHandlerManager = new HandlerManager<>(availableWorkers);
  private HandlerManager<WebSocket> wsHandlerManager = new HandlerManager<>(availableWorkers);

  /**
   * Create an {@code HttpServer}
   */
  public HttpServer() {
    super();
  }

  /**
   * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
   * instances of {@link HttpServerRequest} will be created and passed to this handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  public HttpServer requestHandler(Handler<HttpServerRequest> requestHandler) {
    checkThread();
    this.requestHandler = requestHandler;
    return this;
  }

  /**
   * Get the request handler
   * @return The request handler
   */
  public Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  /**
   * Set the websocket handler for the server to {@code wsHandler}. If a websocket connect handshake is successful a
   * new {@link WebSocket} instance will be created and passed to the handler.
   *
   * @return a reference to this, so methods can be chained.
   */
  public HttpServer websocketHandler(WebSocketHandler wsHandler) {
    checkThread();
    this.wsHandler = wsHandler;
    return this;
  }

  /**
   * Get the websocket handler
   * @return The websocket handler
   */
  public WebSocketHandler websocketHandler() {
    return wsHandler;
  }

  /**
   * Tell the server to start listening on all interfaces and port {@code port}
   *
   * @return a reference to this, so methods can be chained.
   */
  public HttpServer listen(int port) {
    return listen(port, "0.0.0.0");
  }

  /**
   * Tell the server to start listening on port {@code port} and host / ip address given by {@code host}.
   *
   * @return a reference to this, so methods can be chained.
   */
  public HttpServer listen(int port, String host) {
    checkThread();

    if (requestHandler == null && wsHandler == null) {
      throw new IllegalStateException("Set request or websocket handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }

    listening = true;

    synchronized (servers) {
      id = new ServerID(port, host);
      HttpServer shared = servers.get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels");
        ChannelFactory factory =
            new NioServerSocketChannelFactory(
                VertxInternal.instance.getAcceptorPool(),
                availableWorkers);
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setOptions(generateConnectionOptions());

        checkSSL();

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
          public ChannelPipeline getPipeline() {
            ChannelPipeline pipeline = Channels.pipeline();

            if (ssl) {
              SSLEngine engine = context.createSSLEngine();
              engine.setUseClientMode(false);
              switch (clientAuth) {
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
          e.printStackTrace();
        }
        servers.put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = shared;
      }
      if (requestHandler != null) {
        actualServer.reqHandlerManager.addHandler(requestHandler);
      }
      if (wsHandler != null) {
        actualServer.wsHandlerManager.addHandler(wsHandler);
      }
    }
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setSSL(boolean ssl) {
    checkThread();
    return (HttpServer) super.setSSL(ssl);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setKeyStorePath(String path) {
    checkThread();
    return (HttpServer) super.setKeyStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setKeyStorePassword(String pwd) {
    checkThread();
    return (HttpServer) super.setKeyStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setTrustStorePath(String path) {
    checkThread();
    return (HttpServer) super.setTrustStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setTrustStorePassword(String pwd) {
    checkThread();
    return (HttpServer) super.setTrustStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setClientAuthRequired(boolean required) {
    checkThread();
    return (HttpServer) super.setClientAuthRequired(required);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setTCPNoDelay(boolean tcpNoDelay) {
    checkThread();
    return (HttpServer) super.setTCPNoDelay(tcpNoDelay);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setSendBufferSize(int size) {
    checkThread();
    return (HttpServer) super.setSendBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setReceiveBufferSize(int size) {
    checkThread();
    return (HttpServer) super.setReceiveBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setTCPKeepAlive(boolean keepAlive) {
    checkThread();
    return (HttpServer) super.setTCPKeepAlive(keepAlive);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setReuseAddress(boolean reuse) {
    checkThread();
    return (HttpServer) super.setReuseAddress(reuse);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setSoLinger(boolean linger) {
    checkThread();
    return (HttpServer) super.setSoLinger(linger);
  }

  /**
   * {@inheritDoc}
   */
  public HttpServer setTrafficClass(int trafficClass) {
    checkThread();
    return (HttpServer) super.setTrafficClass(trafficClass);
  }

  /**
   * Close the server. Any open HTTP connections will be closed.
   */
  public void close() {
    checkThread();
    close(null);
  }

  /**
   * Close the server. Any open HTTP connections will be closed. {@code doneHandler} will be called when the close
   * is complete.
   */
  public void close(final Handler<Void> done) {
    checkThread();

    if (!listening) return;
    listening = false;

    synchronized (servers) {

      if (requestHandler != null) {
        actualServer.reqHandlerManager.removeHandler(requestHandler);
      }
      if (wsHandler != null) {
        actualServer.wsHandlerManager.removeHandler(wsHandler);
      }

      if (actualServer.reqHandlerManager.hasHandlers() || actualServer.wsHandlerManager.hasHandlers()) {
        // The actual server still has handlers so we don't actually close it
        if (done != null) {
          executeCloseDone(contextID, done);
        }
      } else {
        // No Handlers left so close the actual server
        // The done handler needs to be executed on the context that calls close, NOT the context
        // of the actual server
        actualServer.actualClose(contextID, done);
      }
    }
  }

  private void actualClose(final long closeContextID, final Handler<Void> done) {
    if (id != null) {
      servers.remove(id);
    }

    for (ServerConnection conn : connectionMap.values()) {
      conn.internalClose();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    VertxInternal.instance.setContextID(closeContextID);

    ChannelGroupFuture fut = serverChannelGroup.close();
    if (done != null) {
      fut.addListener(new ChannelGroupFutureListener() {
        public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
          executeCloseDone(closeContextID, done);
        }
      });
    }
  }

  private void executeCloseDone(final long closeContextID, final Handler<Void> done) {
    VertxInternal.instance.executeOnContext(closeContextID, new Runnable() {
      public void run() {
        VertxInternal.instance.setContextID(closeContextID);
        done.handle(null);
      }
    });
  }

  public class ServerHandler extends SimpleChannelUpstreamHandler {

    private void sendError(String err, HttpResponseStatus status, Channel ch) {
      HttpResponse resp = new DefaultHttpResponse(HTTP_1_1, status);
      resp.setChunked(false);
      if (err != null) {
        ChannelBuffer buff = ChannelBuffers.copiedBuffer(err.getBytes(Charset.forName("UTF-8")));
        resp.setHeader("Content-Length", err.length());
        resp.setContent(buff);
      } else {
        resp.setHeader(HttpHeaders.Names.CONTENT_LENGTH, "0");
        //resp.setContent(ChannelBuffers.EMPTY_BUFFER);
      }
      ch.write(resp);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      Object msg = e.getMessage();
      ServerConnection conn = connectionMap.get(ch);
      if (msg instanceof HttpRequest) {
        HttpRequest request = (HttpRequest) msg;

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

          Handshake shake;
          if (Handshake17.matches(request)) {
            shake = new Handshake17();
          } else if (Handshake08.matches(request)) {
            shake = new Handshake08();
          } else if (Handshake00.matches(request)) {
            shake = new Handshake00();
          } else {
            log.error("Unrecognised websockets handshake");
            ch.write(new DefaultHttpResponse(HTTP_1_1, NOT_FOUND));
            return;
          }

           HandlerHolder<WebSocket> firstHandler = null;

          while (true) {
            HandlerHolder<WebSocket> wsHandler = wsHandlerManager.chooseHandler(ch.getWorker());
            if (wsHandler == null || firstHandler == wsHandler) {
              break;
            }

            URI theURI;
            try {
              theURI = new URI(request.getUri());
            } catch (URISyntaxException e2) {
              throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
            }
            WebSocketHandler wsh = (WebSocketHandler)wsHandler.handler;
            if (wsh.accept(theURI.getPath())) {
              conn = new ServerConnection(ch, wsHandler.contextID, ch.getWorker().getThread());
              conn.wsHandler(wsHandler.handler);
              connectionMap.put(ch, conn);
              HttpResponse resp = shake.generateResponse(request);
              ChannelPipeline p = ch.getPipeline();
              p.replace("decoder", "wsdecoder", shake.getDecoder());
              ch.write(resp);
              p.replace("encoder", "wsencoder", shake.getEncoder(true));

              WebSocket ws = new WebSocket(conn);
              conn.handleWebsocketConnect(ws);
              return;
            }

            if (firstHandler == null) {
              firstHandler = wsHandler;
            }
          }
          ch.write(new DefaultHttpResponse(HTTP_1_1, NOT_FOUND));
        } else {
          //HTTP request
          if (conn == null) {
            HandlerHolder<HttpServerRequest> reqHandler = reqHandlerManager.chooseHandler(ch.getWorker());
            if (reqHandler != null) {
              conn = new ServerConnection(ch, reqHandler.contextID, ch.getWorker().getThread());
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
      ch.close();
      final Throwable t = e.getCause();
      if (conn != null && t instanceof Exception) {
        VertxInternal.instance.executeOnContext(conn.getContextID(), new Runnable() {
          public void run() {
            conn.handleException((Exception) t);
          }
        });
      } else {
        t.printStackTrace();
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
        VertxInternal.instance.executeOnContext(conn.getContextID(), new Runnable() {
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
        VertxInternal.instance.executeOnContext(conn.getContextID(), new Runnable() {
          public void run() {
            conn.handleInterestedOpsChanged();
          }
        });
      }
    }
  }
}
