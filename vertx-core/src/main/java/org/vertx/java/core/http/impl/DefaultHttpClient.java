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
import io.netty.buffer.BufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.EventLoopContext;
import org.vertx.java.core.impl.ExceptionDispatchHandler;
import org.vertx.java.core.impl.FlowControlHandler;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.TCPSSLHelper;
import org.vertx.java.core.net.impl.VertxEventLoopGroup;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultHttpClient implements HttpClient {

  private static final Logger log = LoggerFactory.getLogger(HttpClientRequest.class);
  private static final ExceptionDispatchHandler EXCEPTION_DISPATCH_HANDLER = new ExceptionDispatchHandler();

  private final VertxInternal vertx;
  private final Context actualCtx;
  private final EventLoopContext eventLoopContext;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private Bootstrap bootstrap;
  private Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap<Channel, ClientConnection>();
  private Handler<Exception> exceptionHandler;
  private int port = 80;
  private String host = "localhost";
  private final HttpConnectionPool pool = new HttpConnectionPool()  {
    protected void connect(Handler<ClientConnection> connectHandler, Handler<Exception> connectErrorHandler, Context context) {
      internalConnect(connectHandler, connectErrorHandler);
    }
  };
  private boolean keepAlive = true;

  public DefaultHttpClient(VertxInternal vertx) {
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
  }

  public void exceptionHandler(Handler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  public DefaultHttpClient setMaxPoolSize(int maxConnections) {
    pool.setMaxPoolSize(maxConnections);
    return this;
  }

  public int getMaxPoolSize() {
    return pool.getMaxPoolSize();
  }

  public DefaultHttpClient setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  public DefaultHttpClient setPort(int port) {
    this.port = port;
    return this;
  }

  public DefaultHttpClient setHost(String host) {
    this.host = host;
    return this;
  }

  public void connectWebsocket(final String uri, final Handler<WebSocket> wsConnect) {
    connectWebsocket(uri, WebSocketVersion.RFC6455, wsConnect);
  }

  public void connectWebsocket(final String uri, final WebSocketVersion wsVersion, final Handler<WebSocket> wsConnect) {
    getConnection(new Handler<ClientConnection>() {
      public void handle(final ClientConnection conn) {
        if (!conn.isClosed()) {
          conn.toWebSocket(uri, wsConnect, wsVersion);
        } else {
          connectWebsocket(uri, wsVersion, wsConnect);
        }
      }
    }, exceptionHandler, actualCtx);
  }

  public void getNow(String uri, Handler<HttpClientResponse> responseHandler) {
    getNow(uri, null, responseHandler);
  }

  public void getNow(String uri, Map<String, ? extends Object> headers, Handler<HttpClientResponse> responseHandler) {
    HttpClientRequest req = get(uri, responseHandler);
    if (headers != null) {
      req.headers().putAll(headers);
    }
    req.end();
  }

  public HttpClientRequest options(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("OPTIONS", uri, responseHandler);
  }

  public HttpClientRequest get(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("GET", uri, responseHandler);
  }

  public HttpClientRequest head(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("HEAD", uri, responseHandler);
  }

  public HttpClientRequest post(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("POST", uri, responseHandler);
  }

  public HttpClientRequest put(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("PUT", uri, responseHandler);
  }

  public HttpClientRequest delete(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("DELETE", uri, responseHandler);
  }

  public HttpClientRequest trace(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("TRACE", uri, responseHandler);
  }

  public HttpClientRequest connect(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("CONNECT", uri, responseHandler);
  }

  public HttpClientRequest patch(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("PATCH", uri, responseHandler);
  }

  public HttpClientRequest request(String method, String uri, Handler<HttpClientResponse> responseHandler) {
    return new DefaultHttpClientRequest(this, method, uri, responseHandler, actualCtx);
  }

  public void close() {
    pool.close();
    for (ClientConnection conn : connectionMap.values()) {
      conn.internalClose();
    }
  }

  public DefaultHttpClient setSSL(boolean ssl) {
    tcpHelper.setSSL(ssl);
    return this;
  }

  public DefaultHttpClient setVerifyHost(boolean verifyHost) {
    tcpHelper.setVerifyHost(verifyHost);
    return this;
  }

  public DefaultHttpClient setKeyStorePath(String path) {
    tcpHelper.setKeyStorePath(path);
    return this;
  }

  public DefaultHttpClient setKeyStorePassword(String pwd) {
    tcpHelper.setKeyStorePassword(pwd);
    return this;
  }

  public DefaultHttpClient setTrustStorePath(String path) {
    tcpHelper.setTrustStorePath(path);
    return this;
  }

  public DefaultHttpClient setTrustStorePassword(String pwd) {
    tcpHelper.setTrustStorePassword(pwd);
    return this;
  }

  public DefaultHttpClient setTrustAll(boolean trustAll) {
    tcpHelper.setTrustAll(trustAll);
    return this;
  }

  public DefaultHttpClient setTCPNoDelay(boolean tcpNoDelay) {
    tcpHelper.setTCPNoDelay(tcpNoDelay);
    return this;
  }

  public DefaultHttpClient setSendBufferSize(int size) {
    tcpHelper.setSendBufferSize(size);
    return this;
  }

  public DefaultHttpClient setReceiveBufferSize(int size) {
    tcpHelper.setReceiveBufferSize(size);
    return this;
  }

  public DefaultHttpClient setTCPKeepAlive(boolean keepAlive) {
    tcpHelper.setTCPKeepAlive(keepAlive);
    return this;
  }

  public DefaultHttpClient setReuseAddress(boolean reuse) {
    tcpHelper.setReuseAddress(reuse);
    return this;
  }

  public DefaultHttpClient setSoLinger(int linger) {
    if (linger < 0) {
      tcpHelper.setSoLinger(null);
    } else {
      tcpHelper.setSoLinger(linger);
    }
    return this;
  }

  public DefaultHttpClient setTrafficClass(int trafficClass) {
    tcpHelper.setTrafficClass(trafficClass);
    return this;
  }

  public DefaultHttpClient setConnectTimeout(long timeout) {
    tcpHelper.setConnectTimeout(timeout);
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

  public Long getConnectTimeout() {
    return tcpHelper.getConnectTimeout();
  }

  public boolean isSSL() {
    return tcpHelper.isSSL();
  }

  public boolean isVerifyHost() {
    return tcpHelper.isVerifyHost();
  }

  public boolean isTrustAll() {
    return tcpHelper.isTrustAll();
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

  public HttpClient setUsePooledBuffers(boolean pooledBuffers) {
    tcpHelper.setUsePooledBuffers(pooledBuffers);
    return this;
  }

  public boolean isUsePooledBuffers() {
    return tcpHelper.isUsePooledBuffers();
  }

  public void getConnection(Handler<ClientConnection> handler, Handler<Exception> connectionExceptionHandler, Context context) {
    pool.getConnection(handler, connectionExceptionHandler, context);
  }

  void returnConnection(final ClientConnection conn) {
    pool.returnConnection(conn);
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
    } else {
      log.error("Unhandled exception", e);
    }
  }

  /**
   * @return the vertx, for use in package related classes only.
   */
  VertxInternal getVertx() {
    return vertx;
  }

  void internalConnect(final Handler<ClientConnection> connectHandler, final Handler<Exception> connectErrorHandler) {

    if (bootstrap == null) {
      // Share the event loop thread to also serve the HttpClient's network traffic.
      VertxEventLoopGroup pool = new VertxEventLoopGroup();
      pool.addWorker(eventLoopContext.getWorker());
      bootstrap = new Bootstrap();
      bootstrap.group(pool);
      bootstrap.channel(NioSocketChannel.class);
      tcpHelper.checkSSL(vertx);

      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          ChannelPipeline pipeline = ch.pipeline();
          pipeline.addLast("exceptionDispatcher", EXCEPTION_DISPATCH_HANDLER);
          pipeline.addLast("flowControl", new FlowControlHandler());

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

            ChannelFuture fut = sslHandler.handshake();
            fut.addListener(new ChannelFutureListener() {

              public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess()) {
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

  private void connected(final Channel ch, final Handler<ClientConnection> connectHandler) {
    if (actualCtx.isOnCorrectWorker(ch.eventLoop())) {
      vertx.setContext(actualCtx);
      final ClientConnection conn = new ClientConnection(vertx, DefaultHttpClient.this, ch,
                host + ":" + port, tcpHelper.isSSL(), keepAlive, actualCtx);
      conn.closedHandler(new SimpleHandler() {
        public void handle() {
          pool.connectionClosed();
        }
      });
      connectionMap.put(ch, conn);
      connectHandler.handle(conn);
    } else {
        actualCtx.execute(new Runnable() {
          public void run() {
            final ClientConnection conn = new ClientConnection(vertx, DefaultHttpClient.this, ch,
                    host + ":" + port, tcpHelper.isSSL(), keepAlive, actualCtx);
            conn.closedHandler(new SimpleHandler() {
                    public void handle() {
                        pool.connectionClosed();
                    }
                });
            connectionMap.put(ch, conn);
            connectHandler.handle(conn);
          }
        });
    }
  }

  private void failed(final Channel ch, final Handler<Exception> connectionExceptionHandler,
                      final Throwable t) {
    //ch.close();

    // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
    final Handler<Exception> exHandler = connectionExceptionHandler == null ? exceptionHandler : connectionExceptionHandler;

    boolean onEventLoop = actualCtx.isOnCorrectWorker(ch.eventLoop());
    if (onEventLoop) {
      vertx.setContext(actualCtx);
      pool.connectionClosed();
      ch.close();
      if (t instanceof Exception && exHandler != null) {
        exHandler.handle((Exception) t);
      } else {
        log.error("Unhandled exception", t);
      }
    } else {
      actualCtx.execute(new Runnable() {
        public void run() {
          pool.connectionClosed();
          ch.close();
        }
      });

      if (t instanceof Exception && exHandler != null) {
        actualCtx.execute(new Runnable() {
                public void run() {
                    exHandler.handle((Exception) t);
                }
            });
      } else {
        log.error("Unhandled exception", t);
      }
    }
  }

  private class ClientHandler extends VertxHttpHandler<ClientConnection> {
    public ClientHandler() {
      super(DefaultHttpClient.this.connectionMap, vertx);
    }

    @Override
    protected Context getContext(ClientConnection connection) {
      return actualCtx;
    }

    // TODO: Check why we it is different to DefaultNetClient
    @Override
    public void messageReceived(final ChannelHandlerContext chctx, final Object msg) throws Exception {
      final Channel ch = chctx.channel();
      // We need to do this since it's possible the server is being used from a worker context
      if (eventLoopContext.isOnCorrectWorker(ch.eventLoop())) {
        vertx.setContext(actualCtx);
        doMessageReceived(connectionMap.get(ch), chctx, msg);
      } else {
        BufUtil.retain(msg);
        actualCtx.execute(new Runnable() {
          public void run() {
            doMessageReceived(connectionMap.get(ch), chctx, msg);
            BufUtil.release(msg);
          }
        });
      }
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
        if (chunk.data().isReadable()) {
          Buffer buff = new Buffer(chunk.data().slice());
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
