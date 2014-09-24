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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Headers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.SSLHelper;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class HttpClientImpl implements HttpClient {

  private static final Logger log = LoggerFactory.getLogger(HttpClientImpl.class);


  private final VertxInternal vertx;
  private final HttpClientOptions options;
  private final Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap<>();
  private final ContextImpl creatingContext;
  private final ConnectionManager pool;
  private Handler<Throwable> exceptionHandler;
  private final Closeable closeHook;
  private boolean closed;
  private final SSLHelper sslHelper;

  public HttpClientImpl(VertxInternal vertx, HttpClientOptions options) {
    this.vertx = vertx;
    this.options = HttpClientOptions.copiedOptions(options);
    this.sslHelper = new SSLHelper(options, KeyStoreHelper.create(vertx, options.getKeyStoreOptions()), KeyStoreHelper.create(vertx, options.getTrustStoreOptions()));
    this.creatingContext = vertx.getContext();
    closeHook = completionHandler -> {
      HttpClientImpl.this.close();
      completionHandler.handle(Future.completedFuture());
    };
    if (creatingContext != null) {
      if (creatingContext.isMultiThreaded()) {
        throw new IllegalStateException("Cannot use HttpClient in a multi-threaded worker verticle");
      }
      creatingContext.addCloseHook(closeHook);
    }
    pool = new ConnectionManager()  {
      protected void connect(String host, int port, Handler<ClientConnection> connectHandler, Handler<Throwable> connectErrorHandler, ContextImpl context,
                             ConnectionLifeCycleListener listener) {
        internalConnect(context, port, host, connectHandler, connectErrorHandler, listener);
      }
    };
    pool.setKeepAlive(options.isKeepAlive());
    pool.setPipelining(options.isPipelining());
    pool.setMaxSockets(options.getMaxPoolSize());
  }

  @Override
  public synchronized HttpClientImpl exceptionHandler(Handler<Throwable> handler) {
    checkClosed();
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpClient connectWebsocket(WebSocketConnectOptions wsOptions, Handler<WebSocket> wsConnect) {
    checkClosed();
    ContextImpl context = vertx.getOrCreateContext();
    getConnection(wsOptions.getPort(), wsOptions.getHost(), conn -> {
      if (!conn.isClosed()) {
        conn.toWebSocket(wsOptions, wsOptions.getMaxWebsocketFrameSize(), wsConnect);
      } else {
        connectWebsocket(wsOptions, wsConnect);
      }
    }, exceptionHandler, context);
    return this;
  }

  @Override
  public HttpClient getNow(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    get(options, responseHandler).end();
    return this;
  }

  @Override
  public HttpClientRequest options(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("OPTIONS", options, responseHandler);
  }

  @Override
  public HttpClientRequest get(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("GET", options, responseHandler);
  }

  @Override
  public HttpClientRequest head(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("HEAD", options, responseHandler);
  }

  @Override
  public HttpClientRequest post(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("POST", options, responseHandler);
  }

  @Override
  public HttpClientRequest put(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("PUT", options, responseHandler);
  }

  @Override
  public HttpClientRequest delete(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("DELETE", options, responseHandler);
  }

  @Override
  public HttpClientRequest trace(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("TRACE", options, responseHandler);
  }

  @Override
  public HttpClientRequest connect(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("CONNECT", options, connectHandler(responseHandler));
  }

  @Override
  public HttpClientRequest patch(RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    return doRequest("PATCH", options, responseHandler);
  }

  @Override
  public HttpClientRequest request(String method, RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    if (method.equalsIgnoreCase("CONNECT")) {
      // special handling for CONNECT
      responseHandler = connectHandler(responseHandler);
    }
    return doRequest(method, options, responseHandler);
  }

  @Override
  public synchronized void close() {
    checkClosed();
    pool.close();
    for (ClientConnection conn : connectionMap.values()) {
      conn.close();
    }
    if (creatingContext != null) {
      creatingContext.removeCloseHook(closeHook);
    }
    closed = true;
  }

  HttpClientOptions getOptions() {
    return options;
  }

  void getConnection(int port, String host, Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler,
                     ContextImpl context) {
    pool.getConnection(port, host, handler, connectionExceptionHandler, context);
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    } else {
      log.error(e);
    }
  }

  /**
   * @return the vertx, for use in package related classes only.
   */
  VertxInternal getVertx() {
    return vertx;
  }

  SSLHelper getSslHelper() {
    return sslHelper;
  }

  void removeChannel(Channel channel) {
    connectionMap.remove(channel);
  }

  private void applyConnectionOptions(Bootstrap bootstrap) {
    bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    if (options.getSendBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    if (options.getTrafficClass() != -1) {
      bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());
    bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
  }

  private void internalConnect(ContextImpl context, int port, String host, Handler<ClientConnection> connectHandler, Handler<Throwable> connectErrorHandler,
                               ConnectionLifeCycleListener listener) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.getEventLoop());
    bootstrap.channel(NioSocketChannel.class);
    sslHelper.checkSSL(vertx);
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (options.isSsl()) {
          SSLEngine engine = sslHelper.getSslContext().createSSLEngine(host, port);
          engine.setUseClientMode(true); // We are on the client side of the connection
          if (options.isVerifyHost()) {
            SSLParameters sslParameters = engine.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(sslParameters);
          }
          pipeline.addLast("ssl", new SslHandler(engine));
        }

        pipeline.addLast("codec", new HttpClientCodec(4096, 8192, 8192, false, false));
        if (options.isTryUseCompression()) {
          pipeline.addLast("inflater", new HttpContentDecompressor(true));
        }
        if (options.getIdleTimeout() > 0) {
          pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
        }
        pipeline.addLast("handler", new ClientHandler(context));
      }
    });
    applyConnectionOptions(bootstrap);
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        Channel ch = channelFuture.channel();
        if (channelFuture.isSuccess()) {
          if (options.isSsl()) {
            // TCP connected, so now we must do the SSL handshake

            SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

            io.netty.util.concurrent.Future<Channel> fut = sslHandler.handshakeFuture();
            fut.addListener(future -> {
              if (future.isSuccess()) {
                connected(context, port, host, ch, connectHandler, listener);
              } else {
                connectionFailed(context, ch, connectErrorHandler, new SSLHandshakeException("Failed to create SSL connection"),
                                 listener);
              }
            });
          } else {
            connected(context, port, host, ch, connectHandler, listener);
          }
        } else {
          connectionFailed(context, ch, connectErrorHandler, channelFuture.cause(), listener);
        }
      }
    });
  }

  private HttpClientRequest doRequest(String method, RequestOptions options, Handler<HttpClientResponse> responseHandler) {
    Objects.requireNonNull(options, "no null options accepted");
    Objects.requireNonNull(responseHandler, "no null responseHandler accepted");
    checkClosed();
    HttpClientRequest req = new HttpClientRequestImpl(this, method, options, responseHandler, vertx);
    if (options.getHeaders() != null) {
      req.headers().setAll(options.getHeaders());
    }
    return req;
  }

  private synchronized void checkClosed() {
    if (closed) {
      throw new IllegalStateException("Client is closed");
    }
  }

  private void connected(ContextImpl context, int port, String host, Channel ch, Handler<ClientConnection> connectHandler, ConnectionLifeCycleListener listener) {
    context.execute(() -> createConn(context, port, host, ch, connectHandler, listener), true);
  }

  private void createConn(ContextImpl context, int port, String host, Channel ch, Handler<ClientConnection> connectHandler, ConnectionLifeCycleListener listener) {
    ClientConnection conn = new ClientConnection(vertx, HttpClientImpl.this, ch,
        options.isSsl(), host, port, context, listener);
    conn.closeHandler(v -> {
      // The connection has been closed - tell the pool about it, this allows the pool to create more
      // connections. Note the pool doesn't actually remove the connection, when the next person to get a connection
      // gets the closed on, they will check if it's closed and if so get another one.
      listener.connectionClosed(conn);
    });
    connectionMap.put(ch, conn);
    connectHandler.handle(conn);
  }

  private void connectionFailed(ContextImpl context, Channel ch, Handler<Throwable> connectionExceptionHandler,
                                Throwable t, ConnectionLifeCycleListener listener) {
    // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
    // If that doesn't exist just log it
    Handler<Throwable> exHandler =
      connectionExceptionHandler == null ? (exceptionHandler == null ? log::error : exceptionHandler ): connectionExceptionHandler;

    context.execute(() -> {
      listener.connectionClosed(null);
      try {
        ch.close();
      } catch (Exception ignore) {
      }
      if (exHandler != null) {
        exHandler.handle(t);
      } else {
        log.error(t);
      }
    }, true);
  }

  private Handler<HttpClientResponse> connectHandler(Handler<HttpClientResponse> responseHandler) {
    Objects.requireNonNull(responseHandler, "no null responseHandler accepted");
    return resp -> {
      HttpClientResponse response;
      if (resp.statusCode() == 200) {
        // connect successful force the modification of the ChannelPipeline
        // beside this also pause the socket for now so the user has a chance to register its dataHandler
        // after received the NetSocket
        NetSocket socket = resp.netSocket();
        socket.pause();

        response = new HttpClientResponse() {
          private boolean resumed;

          @Override
          public int statusCode() {
            return resp.statusCode();
          }

          @Override
          public String statusMessage() {
            return resp.statusMessage();
          }

          @Override
          public Headers headers() {
            return resp.headers();
          }

          @Override
          public Headers trailers() {
            return resp.trailers();
          }

          @Override
          public List<String> cookies() {
            return resp.cookies();
          }

          @Override
          public HttpClientResponse bodyHandler(Handler<Buffer> bodyHandler) {
            resp.bodyHandler(bodyHandler);
            return this;
          }

          @Override
          public NetSocket netSocket() {
            if (!resumed) {
              resumed = true;
              vertx.getContext().execute(socket::resume, false); // resume the socket now as the user had the chance to register a dataHandler
            }
            return socket;
          }

          @Override
          public HttpClientResponse endHandler(Handler<Void> endHandler) {
            resp.endHandler(endHandler);
            return this;
          }

          @Override
          public HttpClientResponse handler(Handler<Buffer> handler) {
            resp.handler(handler);
            return this;
          }

          @Override
          public HttpClientResponse pause() {
            resp.pause();
            return this;
          }

          @Override
          public HttpClientResponse resume() {
            resp.resume();
            return this;
          }

          @Override
          public HttpClientResponse exceptionHandler(Handler<Throwable> handler) {
            resp.exceptionHandler(handler);
            return this;
          }
        };
      } else {
        response = resp;
      }
      responseHandler.handle(response);
    };
  }

  private class ClientHandler extends VertxHttpHandler<ClientConnection> {
    private boolean closeFrameSent;
    private ContextImpl context;

    public ClientHandler(ContextImpl context) {
      super(vertx, HttpClientImpl.this.connectionMap);
      this.context = context;
    }

    @Override
    protected ContextImpl getContext(ClientConnection connection) {
      return context;
    }

    @Override
    protected void doMessageReceived(ClientConnection conn, ChannelHandlerContext ctx, Object msg) {
      if (conn == null) {
        return;
      }
      boolean valid = false;
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) msg;
        conn.handleResponse(response);
        valid = true;
      }
      if (msg instanceof HttpContent) {
        HttpContent chunk = (HttpContent) msg;
        if (chunk.content().isReadable()) {
          Buffer buff = Buffer.buffer(chunk.content().slice());
          conn.handleResponseChunk(buff);
        }
        if (chunk instanceof LastHttpContent) {
          conn.handleResponseEnd((LastHttpContent)chunk);
        }
        valid = true;
      } else if (msg instanceof WebSocketFrameInternal) {
        WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
        switch (frame.type()) {
          case BINARY:
          case CONTINUATION:
          case TEXT:
            conn.handleWsFrame(frame);
            break;
          case PING:
            // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
            ctx.writeAndFlush(new WebSocketFrameImpl(FrameType.PONG, frame.getBinaryData()));
            break;
          case CLOSE:
            if (!closeFrameSent) {
              // Echo back close frame and close the connection once it was written.
              // This is specified in the WebSockets RFC 6455 Section  5.4.1
              ctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
              closeFrameSent = true;
            }
            break;
          default:
            throw new IllegalStateException("Invalid type: " + frame.type());
        }
        valid = true;
      }
      if (!valid) {
        throw new IllegalStateException("Invalid object " + msg);
      }
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
}
