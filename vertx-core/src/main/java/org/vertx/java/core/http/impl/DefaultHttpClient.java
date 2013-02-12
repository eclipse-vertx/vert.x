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

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.ssl.SslHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.ConnectionPool;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.EventLoopContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.TCPSSLHelper;
import org.vertx.java.core.net.impl.VertxWorkerPool;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultHttpClient implements HttpClient {

  private static final Logger log = LoggerFactory.getLogger(HttpClientRequest.class);

  private final VertxInternal vertx;
  private final Context actualCtx;
  private final EventLoopContext eventLoopContext;
  private final TCPSSLHelper tcpHelper = new TCPSSLHelper();
  private ClientBootstrap bootstrap;
  private Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap<Channel, ClientConnection>();
  private Handler<Exception> exceptionHandler;
  private int port = 80;
  private String host = "localhost";
  private final ConnectionPool<ClientConnection> pool = new ConnectionPool<ClientConnection>() {
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

  public DefaultHttpClient setSoLinger(boolean linger) {
    tcpHelper.setSoLinger(linger);
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

  public Boolean isSoLinger() {
    return tcpHelper.isSoLinger();
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

  private void internalConnect(final Handler<ClientConnection> connectHandler, final Handler<Exception> connectErrorHandler) {

    if (bootstrap == null) {
      // Share the event loop thread to also serve the HttpClient's network traffic.
      VertxWorkerPool pool = new VertxWorkerPool();
      pool.addWorker(eventLoopContext.getWorker());
      NioClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory(
          vertx.getClientAcceptorPool(), pool);
      bootstrap = new ClientBootstrap(channelFactory);

      tcpHelper.checkSSL(vertx);

      bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        public ChannelPipeline getPipeline() throws Exception {
          ChannelPipeline pipeline = Channels.pipeline();
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
          pipeline.addLast("codec", new SwitchingHttpClientCodec());
          pipeline.addLast("handler", new ClientHandler());
          return pipeline;
        }
      });
    }
    bootstrap.setOptions(tcpHelper.generateConnectionOptions(false));
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {

        final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();

        if (channelFuture.isSuccess()) {

          if (tcpHelper.isSSL()) {
            // TCP connected, so now we must do the SSL handshake

            SslHandler sslHandler = (SslHandler)ch.getPipeline().get("ssl");

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
          failed(ch, connectErrorHandler, channelFuture.getCause());
        }
      }
    });
  }

  private void connected(final NioSocketChannel ch, final Handler<ClientConnection> connectHandler) {
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

  private void failed(final NioSocketChannel ch, final Handler<Exception> connectionExceptionHandler,
                      final Throwable t) {
    //ch.close();

    // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
    final Handler<Exception> exHandler = connectionExceptionHandler == null ? exceptionHandler : connectionExceptionHandler;

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

  private class ClientHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelClosed(ChannelHandlerContext chctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ClientConnection conn = connectionMap.remove(ch);
      if (conn != null) {
        actualCtx.execute(new Runnable() {
          public void run() {
            conn.handleClosed();
          }
        });
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext chctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ClientConnection conn = connectionMap.get(ch);
      actualCtx.execute(new Runnable() {
        public void run() {
          conn.handleInterestedOpsChanged();
        }
      });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext chctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ClientConnection conn = connectionMap.get(ch);
      final Throwable t = e.getCause();
      if (conn != null && t instanceof Exception) {
        actualCtx.execute(new Runnable() {
          public void run() {
            conn.handleException((Exception) t);
          }
        });
      } else {
        // Ignore - any exceptions before a channel exists will be passed manually via the failed(...) method
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext chctx, final MessageEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      // We need to do this since it's possible the server is being used from a worker context
      if (eventLoopContext.isOnCorrectWorker(ch.getWorker())) {
        doMessageReceived(ch, e);
      } else {
        actualCtx.execute(new Runnable() {
          public void run() {
            doMessageReceived(ch, e);
          }
        });
      }
    }

    private void doMessageReceived(Channel ch, MessageEvent e) {
      ClientConnection conn = connectionMap.get(ch);
      Object msg = e.getMessage();
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) msg;

        conn.handleResponse(response);
        ChannelBuffer content = response.getContent();
        if (content.readable()) {
          conn.handleResponseChunk(new Buffer(content));
        }
        if (!response.isChunked() && (response.getStatus().getCode() != 100)) {
          conn.handleResponseEnd();
        }
      } else if (msg instanceof HttpChunk) {
        HttpChunk chunk = (HttpChunk) msg;
        if (chunk.getContent().readable()) {
          Buffer buff = new Buffer(chunk.getContent());
          conn.handleResponseChunk(buff);
        }
        if (chunk.isLast()) {
          if (chunk instanceof HttpChunkTrailer) {
            HttpChunkTrailer trailer = (HttpChunkTrailer) chunk;
            conn.handleResponseEnd(trailer);
          } else {
            conn.handleResponseEnd();
          }
        }
      } else if (msg instanceof WebSocketFrame) {
        WebSocketFrame frame = (WebSocketFrame) msg;
        conn.handleWsFrame(frame);
      } else {
        throw new IllegalStateException("Invalid object " + e.getMessage());
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
