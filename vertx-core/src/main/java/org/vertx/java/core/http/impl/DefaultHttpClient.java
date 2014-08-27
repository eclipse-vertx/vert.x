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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrameInternal;
import org.vertx.java.core.impl.Closeable;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.TCPSSLHelper;
import org.vertx.java.core.net.impl.VertxEventLoopGroup;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultHttpClient implements HttpClient {

  final VertxInternal vertx;
  final Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap<>();

  private final DefaultContext actualCtx;
  final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private Bootstrap bootstrap;
  private Handler<Throwable> exceptionHandler;
  private int port = 80;
  private String host = "localhost";
  private boolean tryUseCompression;
  private int maxWebSocketFrameSize = 65536;

  private final HttpPool pool = new PriorityHttpConnectionPool()  {
    protected void connect(Handler<ClientConnection> connectHandler, Handler<Throwable> connectErrorHandler, DefaultContext context) {
      internalConnect(connectHandler, connectErrorHandler);
    }
  };
  private boolean keepAlive = true;
  private boolean pipelining = true;
  private int connectionMaxOutstandingRequest = -1;
  private boolean configurable = true;
  private boolean closed;
  private final Closeable closeHook = new Closeable() {
    @Override
    public void close(Handler<AsyncResult<Void>> doneHandler) {
      DefaultHttpClient.this.close();
      doneHandler.handle(new DefaultFutureResult<>((Void)null));
    }
  };

  public DefaultHttpClient(VertxInternal vertx) {
    this.vertx = vertx;
    actualCtx = vertx.getOrCreateContext();
    actualCtx.addCloseHook(closeHook);
  }

  // @Override
  public DefaultHttpClient exceptionHandler(Handler<Throwable> handler) {
    checkClosed();
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public DefaultHttpClient setMaxPoolSize(int maxConnections) {
    checkClosed();
    checkConfigurable();
    pool.setMaxPoolSize(maxConnections);
    return this;
  }

  @Override
  public int getMaxPoolSize() {
    checkClosed();
    return pool.getMaxPoolSize();
  }

  @Override
  public DefaultHttpClient setKeepAlive(boolean keepAlive) {
    checkClosed();
    checkConfigurable();
    this.keepAlive = keepAlive;
    return this;
  }

  @Override
  public boolean isKeepAlive() {
    checkClosed();
    return keepAlive;
  }

  @Override
  public DefaultHttpClient setPipelining(boolean pipelining) {
    checkClosed();
    checkConfigurable();
    this.pipelining = pipelining;
    return this;
  }

  @Override
  public boolean isPipelining() {
    checkClosed();
    return pipelining;
  }

  @Override
  public DefaultHttpClient setPort(int port) {
    checkClosed();
    checkConfigurable();
    this.port = port;
    return this;
  }

  @Override
  public int getPort() {
    checkClosed();
    return port;
  }

  @Override
  public DefaultHttpClient setHost(String host) {
    checkClosed();
    checkConfigurable();
    this.host = host;
    return this;
  }

  @Override
  public String getHost() {
    checkClosed();
    return host;
  }

  @Override
  public HttpClient connectWebsocket(final String uri, final Handler<WebSocket> wsConnect) {
    checkClosed();
    connectWebsocket(uri, WebSocketVersion.RFC6455, wsConnect);
    return this;
  }

  @Override
  public HttpClient connectWebsocket(final String uri, final WebSocketVersion wsVersion, final Handler<WebSocket> wsConnect) {
    checkClosed();
    connectWebsocket(uri, wsVersion, null, wsConnect);
    return this;
  }

  @Override
  public HttpClient connectWebsocket(final String uri, final WebSocketVersion wsVersion, final MultiMap headers, final Handler<WebSocket> wsConnect) {
    connectWebsocket(uri, wsVersion, headers, null, wsConnect);
    return this;
  }

  @Override
  public HttpClient connectWebsocket(final String uri, final WebSocketVersion wsVersion, final MultiMap headers, final Set<String> subprotocols, final Handler<WebSocket> wsConnect) {
    checkClosed();
    configurable = false;
    getConnection(new Handler<ClientConnection>() {
      public void handle(final ClientConnection conn) {
        if (!conn.isClosed()) {
          conn.toWebSocket(uri, wsVersion, headers, maxWebSocketFrameSize, subprotocols, wsConnect);
        } else {
          connectWebsocket(uri, wsVersion, headers, subprotocols, wsConnect);
        }
      }
    }, exceptionHandler, actualCtx);
    return this;
  }

  @Override
  public HttpClient getNow(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    getNow(uri, null, responseHandler);
    return this;
  }

  @Override
  public HttpClient getNow(String uri, MultiMap headers, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    HttpClientRequest req = get(uri, responseHandler);
    if (headers != null) {
      req.headers().set(headers);
    }
    req.end();
    return this;
  }

  @Override
  public HttpClientRequest options(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("OPTIONS", uri, responseHandler);
  }

  @Override
  public HttpClientRequest get(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("GET", uri, responseHandler);
  }

  @Override
  public HttpClientRequest head(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("HEAD", uri, responseHandler);
  }

  @Override
  public HttpClientRequest post(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("POST", uri, responseHandler);
  }

  @Override
  public HttpClientRequest put(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("PUT", uri, responseHandler);
  }

  @Override
  public HttpClientRequest delete(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("DELETE", uri, responseHandler);
  }

  @Override
  public HttpClientRequest trace(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("TRACE", uri, responseHandler);
  }

  @Override
  public HttpClientRequest connect(String uri, final Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("CONNECT", uri, connectHandler(responseHandler));
  }

  private Handler<HttpClientResponse> connectHandler(final Handler<HttpClientResponse> responseHandler) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(final HttpClientResponse event) {
        HttpClientResponse response;
        if (event.statusCode() == 200) {
          // connect successful force the modification of the ChannelPipeline
          // beside this also pause the socket for now so the user has a chance to register its dataHandler
          // after received the NetSocket
          final NetSocket socket = event.netSocket();
          socket.pause();

          response = new HttpClientResponse() {
            private boolean resumed;

            @Override
            public int statusCode() {
              return event.statusCode();
            }

            @Override
            public String statusMessage() {
              return event.statusMessage();
            }

            @Override
            public MultiMap headers() {
              return event.headers();
            }

            @Override
            public MultiMap trailers() {
              return event.trailers();
            }

            @Override
            public List<String> cookies() {
              return event.cookies();
            }

            @Override
            public HttpClientResponse bodyHandler(Handler<Buffer> bodyHandler) {
              event.bodyHandler(bodyHandler);
              return this;
            }

            @Override
            public NetSocket netSocket() {
              if (!resumed) {
                resumed = true;
                vertx.getContext().execute(new Runnable() {
                  @Override
                  public void run() {
                    // resume the socket now as the user had the chance to register a dataHandler
                    socket.resume();
                  }
                });
              }
              return socket;

            }

            @Override
            public HttpClientResponse endHandler(Handler<Void> endHandler) {
              event.endHandler(endHandler);
              return this;
            }

            @Override
            public HttpClientResponse dataHandler(Handler<Buffer> handler) {
              event.dataHandler(handler);
              return this;
            }

            @Override
            public HttpClientResponse pause() {
              event.pause();
              return this;
            }

            @Override
            public HttpClientResponse resume() {
              event.resume();
              return this;
            }

            @Override
            public HttpClientResponse exceptionHandler(Handler<Throwable> handler) {
              event.exceptionHandler(handler);
              return this;
            }
          };
        } else {
          response = event;
        }
        responseHandler.handle(response);
      }
    };
  }

  @Override
  public HttpClientRequest patch(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("PATCH", uri, responseHandler);
  }

  @Override
  public HttpClientRequest request(String method, String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    if (method.equalsIgnoreCase("CONNECT")) {
      // special handling for CONNECT
      responseHandler = connectHandler(responseHandler);
    }
    return doRequest(method, uri, responseHandler);
  }

  @Override
  public void close() {
    checkClosed();
    pool.close();
    for (ClientConnection conn : connectionMap.values()) {
      conn.close();
    }
    actualCtx.removeCloseHook(closeHook);
    closed = true;
  }

  @Override
  public DefaultHttpClient setSSL(boolean ssl) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setSSL(ssl);
    return this;
  }

  @Override
  public DefaultHttpClient setVerifyHost(boolean verifyHost) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setVerifyHost(verifyHost);
    return this;
  }

  @Override
  public HttpClient setSSLContext(SSLContext sslContext) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setExternalSSLContext(sslContext);
    return this;
  }

  @Override
  public DefaultHttpClient setKeyStorePath(String path) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  @Override
  public DefaultHttpClient setKeyStorePassword(String pwd) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  @Override
  public DefaultHttpClient setTrustStorePath(String path) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  @Override
  public DefaultHttpClient setTrustStorePassword(String pwd) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  @Override
  public DefaultHttpClient setTrustAll(boolean trustAll) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setTrustAll(trustAll);
    return this;
  }

  @Override
  public DefaultHttpClient setTCPNoDelay(boolean tcpNoDelay) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  @Override
  public DefaultHttpClient setSendBufferSize(int size) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  @Override
  public DefaultHttpClient setReceiveBufferSize(int size) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  @Override
  public DefaultHttpClient setTCPKeepAlive(boolean keepAlive) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  @Override
  public DefaultHttpClient setReuseAddress(boolean reuse) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  @Override
  public DefaultHttpClient setSoLinger(int linger) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setSoLinger(linger);
    return this;
  }

  @Override
  public DefaultHttpClient setTrafficClass(int trafficClass) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public DefaultHttpClient setConnectTimeout(int timeout) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setConnectTimeout(timeout);
    return this;
  }

  @Override
  public boolean isTCPNoDelay() {
    checkClosed();
    return tcpHelper.isTCPNoDelay();
  }

  @Override
  public int getSendBufferSize() {
    checkClosed();
    return tcpHelper.getSendBufferSize();
  }

  @Override
  public int getReceiveBufferSize() {
    checkClosed();
    return tcpHelper.getReceiveBufferSize();
  }

  @Override
  public boolean isTCPKeepAlive() {
    checkClosed();
    return tcpHelper.isTCPKeepAlive();
  }

  @Override
  public boolean isReuseAddress() {
    checkClosed();
    return tcpHelper.isReuseAddress();
  }

  @Override
  public int getSoLinger() {
    checkClosed();
    return tcpHelper.getSoLinger();
  }

  @Override
  public int getTrafficClass() {
    checkClosed();
    return tcpHelper.getTrafficClass();
  }

  @Override
  public int getConnectTimeout() {
    checkClosed();
    return tcpHelper.getConnectTimeout();
  }

  @Override
  public boolean isSSL() {
    checkClosed();
    return tcpHelper.isSSL();
  }

  @Override
  public boolean isVerifyHost() {
    checkClosed();
    return tcpHelper.isVerifyHost();
  }

  @Override
  public boolean isTrustAll() {
    checkClosed();
    return tcpHelper.isTrustAll();
  }

  @Override
  public String getKeyStorePath() {
    checkClosed();
    return tcpHelper.getKeyStorePath();
  }

  @Override
  public String getKeyStorePassword() {
    checkClosed();
    return tcpHelper.getKeyStorePassword();
  }

  @Override
  public String getTrustStorePath() {
    checkClosed();
    return tcpHelper.getTrustStorePath();
  }

  @Override
  public String getTrustStorePassword() {
    checkClosed();
    return tcpHelper.getTrustStorePassword();
  }

  @Override
  public HttpClient setUsePooledBuffers(boolean pooledBuffers) {
    checkClosed();
    checkConfigurable();
    tcpHelper.setUsePooledBuffers(pooledBuffers);
    return this;
  }

  @Override
  public boolean isUsePooledBuffers() {
    checkClosed();
    return tcpHelper.isUsePooledBuffers();
  }

  @Override
  public HttpClient setTryUseCompression(boolean tryUseCompression) {
    checkClosed();
    this.tryUseCompression = tryUseCompression;
    return this;
  }

  @Override
  public boolean getTryUseCompression() {
    return tryUseCompression;
  }

  @Override
  public HttpClient setMaxWebSocketFrameSize(int maxSize) {
    maxWebSocketFrameSize = maxSize;
    return this;
  }

  @Override
  public int getMaxWebSocketFrameSize() {
    return maxWebSocketFrameSize;
  }

  @Override
  public HttpClient setMaxWaiterQueueSize(int maxWaiterQueueSize) {
    checkClosed();
    pool.setMaxWaiterQueueSize(maxWaiterQueueSize);
    return this;
  }

  @Override
  public int getMaxWaiterQueueSize() {
    checkClosed();
    return pool.getMaxWaiterQueueSize();
  }

  @Override
  public HttpClient setConnectionMaxOutstandingRequestCount(int connectionMaxOutstandingRequestCount) {
    checkClosed();
    connectionMaxOutstandingRequest = connectionMaxOutstandingRequestCount;
    return this;
  }

  @Override
  public int getConnectionMaxOutstandingRequestCount() {
    checkClosed();
    return connectionMaxOutstandingRequest;
  }

  void getConnection(Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, DefaultContext context) {
    pool.getConnection(handler, connectionExceptionHandler, context);
  }

  void returnConnection(final ClientConnection conn) {
    // prevent connection from taking more requests if it's fully occupied.
    if (!conn.isFullyOccupied()) {
      pool.returnConnection(conn);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    } else {
      vertx.reportException(e);
    }
  }

  /**
   * @return the vertx, for use in package related classes only.
   */
  VertxInternal getVertx() {
    return vertx;
  }

  void internalConnect(final Handler<ClientConnection> connectHandler, final Handler<Throwable> connectErrorHandler) {
    if (bootstrap == null) {
      // Share the event loop thread to also serve the HttpClient's network traffic.
      VertxEventLoopGroup pool = new VertxEventLoopGroup();
      pool.addWorker(actualCtx.getEventLoop());
      bootstrap = new Bootstrap();
      bootstrap.group(pool);
      bootstrap.channel(NioSocketChannel.class);
      tcpHelper.checkSSL(vertx);

      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          ChannelPipeline pipeline = ch.pipeline();
          if (tcpHelper.isSSL()) {
            SSLEngine engine = tcpHelper.getSSLContext().createSSLEngine(host, port);
            engine.setUseClientMode(true); //We are on the client side of the connection
            if (tcpHelper.isVerifyHost()) {
              SSLParameters sslParameters = engine.getSSLParameters();
              sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
              engine.setSSLParameters(sslParameters);
            }
            pipeline.addLast("ssl", new SslHandler(engine));
          }

          pipeline.addLast("codec", new HttpClientCodec(4096, 8192, 8192, false, false));
          if (tryUseCompression) {
            pipeline.addLast("inflater", new HttpContentDecompressor(true));
          }
          pipeline.addLast("handler", new ClientHandler());
        }
      });
    }
    tcpHelper.applyConnectionOptions(bootstrap);
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        final Channel ch = channelFuture.channel();
        if (channelFuture.isSuccess()) {
          if (tcpHelper.isSSL()) {
            // TCP connected, so now we must do the SSL handshake

            SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

            Future<Channel> fut = sslHandler.handshakeFuture();
            fut.addListener(new GenericFutureListener<Future<Channel>>() {
              @Override
              public void operationComplete(Future<Channel> future) throws Exception {
                if (future.isSuccess()) {
                  connected(ch, connectHandler);
                } else {
                  connectionFailed(ch, connectErrorHandler, new SSLHandshakeException("Failed to create SSL connection"));
                }
              }
            });
          } else {
            connected(ch, connectHandler);
          }
        } else {
          connectionFailed(ch, connectErrorHandler, channelFuture.cause());
        }
      }
    });
  }

  private HttpClientRequest doRequest(String method, String uri, Handler<HttpClientResponse> responseHandler) {
    configurable = false;
    return new DefaultHttpClientRequest(this, method, uri, responseHandler, actualCtx);
  }

  private final void checkClosed() {
    if (closed) {
      throw new IllegalStateException("Client is closed");
    }
  }

  private final void checkConfigurable() {
    if (!configurable) {
      throw new IllegalStateException("Can't set property after connect has been called");
    }
  }

  private void connected(final Channel ch, final Handler<ClientConnection> connectHandler) {
    actualCtx.execute(ch.eventLoop(), new Runnable() {
      public void run() {
        createConn(ch, connectHandler);
      }
    });
  }

  private void createConn(Channel ch, Handler<ClientConnection> connectHandler) {
    final ClientConnection conn = new ClientConnection(vertx, DefaultHttpClient.this, ch,
        tcpHelper.isSSL(), host, port, keepAlive, pipelining, actualCtx);
    conn.closeHandler(new VoidHandler() {
      public void handle() {
        // The connection has been closed - tell the pool about it, this allows the pool to create more
        // connections. Note the pool doesn't actually remove the connection, when the next person to get a connection
        // gets the closed on, they will check if it's closed and if so get another one.
        pool.connectionClosed(conn);
      }
    });
    conn.setMaxOutstandingRequestCount(connectionMaxOutstandingRequest);
    connectionMap.put(ch, conn);
    connectHandler.handle(conn);
  }

  private void connectionFailed(final Channel ch, final Handler<Throwable> connectionExceptionHandler,
                                final Throwable t) {
    // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
    final Handler<Throwable> exHandler = connectionExceptionHandler == null ? exceptionHandler : connectionExceptionHandler;

    actualCtx.execute(ch.eventLoop(), new Runnable() {
      public void run() {
        pool.connectionClosed(null);
        try {
          ch.close();
        } catch (Exception ignore) {
        }
        if (exHandler != null) {
          exHandler.handle(t);
        } else {
          actualCtx.reportException(t);
        }
      }
    });
  }

  private class ClientHandler extends VertxHttpHandler<ClientConnection> {
    private boolean closeFrameSent;

    public ClientHandler() {
      super(vertx, DefaultHttpClient.this.connectionMap);
    }

    @Override
    protected DefaultContext getContext(ClientConnection connection) {
      return actualCtx;
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
          Buffer buff = new Buffer(chunk.content().slice());
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
            ctx.writeAndFlush(new DefaultWebSocketFrame(WebSocketFrame.FrameType.PONG, frame.getBinaryData()));
            break;
          case CLOSE:
            if (!closeFrameSent) {
              // Echo back close frame and close the connection once it was written.
              // This is specified in the WebSockets RFC 6455 Section  5.4.1
              ctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
              closeFrameSent = true;
            }
            break;
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
