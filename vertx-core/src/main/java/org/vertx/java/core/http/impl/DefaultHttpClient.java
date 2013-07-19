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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.*;
import org.vertx.java.core.net.impl.TCPSSLHelper;
import org.vertx.java.core.net.impl.VertxEventLoopGroup;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultHttpClient implements HttpClient {

  private static final ExceptionDispatchHandler EXCEPTION_DISPATCH_HANDLER = new ExceptionDispatchHandler();

  final VertxInternal vertx;
  final Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap<>();

  private final DefaultContext actualCtx;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private Bootstrap bootstrap;
  private Handler<Throwable> exceptionHandler;
  private int port = 80;
  private String host = "localhost";
  private final HttpPool pool = new PriorityHttpConnectionPool()  {
    protected void connect(Handler<ClientConnection> connectHandler, Handler<Throwable> connectErrorHandler, DefaultContext context) {
      internalConnect(connectHandler, connectErrorHandler);
    }
  };
  private boolean keepAlive = true;
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
    checkClosed();
    configurable = false;
    getConnection(new Handler<ClientConnection>() {
      public void handle(final ClientConnection conn) {
        if (!conn.isClosed()) {
          conn.toWebSocket(uri, wsVersion, headers, wsConnect);
        } else {
          connectWebsocket(uri, wsVersion, headers, wsConnect);
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
  public HttpClientRequest connect(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("CONNECT", uri, responseHandler);
  }

  @Override
  public HttpClientRequest patch(String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
    return doRequest("PATCH", uri, responseHandler);
  }

  @Override
  public HttpClientRequest request(String method, String uri, Handler<HttpClientResponse> responseHandler) {
    checkClosed();
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

  void getConnection(Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, DefaultContext context) {
    pool.getConnection(handler, connectionExceptionHandler, context);
  }

  void returnConnection(final ClientConnection conn) {
    pool.returnConnection(conn);
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
          pipeline.addLast("exceptionDispatcher", EXCEPTION_DISPATCH_HANDLER);

          if (tcpHelper.isSSL()) {
            SSLEngine engine = tcpHelper.getSSLContext().createSSLEngine(host, port);
            if (tcpHelper.isVerifyHost()) {
              SSLParameters sslParameters = engine.getSSLParameters();
              sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
              engine.setSSLParameters(sslParameters);
            }
            engine.setUseClientMode(true); //We are on the client side of the connection
            pipeline.addLast("ssl", new SslHandler(engine));
          }
          //pipeline.addLast("byteBufHandler", ByteBufHandler.INSTANCE);

          pipeline.addLast("codec", new HttpClientCodec());
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
                  failed(ch, connectErrorHandler, new SSLHandshakeException("Failed to create SSL connection"));
                }
              }
            });
          } else {
            connected(ch, connectHandler);
          }
        } else {
          failed(ch, connectErrorHandler, channelFuture.cause());
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
        tcpHelper.isSSL(), host, port, keepAlive, actualCtx);
    conn.closeHandler(new VoidHandler() {
      public void handle() {
        // The connection has been closed - tell the pool about it, this allows the pool to create more
        // connections. Note the pool doesn't actually remove the connection, when the next person to get a connection
        // gets the closed on, they will check if it's closed and if so get another one.
        pool.connectionClosed();
      }
    });
    connectionMap.put(ch, conn);
    connectHandler.handle(conn);
  }

  private void failed(final Channel ch, final Handler<Throwable> connectionExceptionHandler,
                      final Throwable t) {
    // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
    final Handler<Throwable> exHandler = connectionExceptionHandler == null ? exceptionHandler : connectionExceptionHandler;

    actualCtx.execute(ch.eventLoop(), new Runnable() {
      public void run() {
        pool.connectionClosed();
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
    public ClientHandler() {
      super(vertx, DefaultHttpClient.this.connectionMap);
    }

    @Override
    protected DefaultContext getContext(ClientConnection connection) {
      return actualCtx;
    }

    @Override
    protected void doMessageReceived(ClientConnection conn, ChannelHandlerContext ctx, Object msg) {
      if (conn == null || conn.isClosed()) {
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
      } else if (msg instanceof WebSocketFrame) {
        WebSocketFrame frame = (WebSocketFrame) msg;
        conn.handleWsFrame(frame);
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
