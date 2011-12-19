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

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.ssl.SslHandler;
import org.vertx.java.core.ConnectionPool;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.ws.WebSocketFrame;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClientBase;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>An asynchronous, pooling, HTTP 1.1 client</p>
 * <p/>
 * <p>An {@code HttpClient} maintains a pool of connections to a specific host, at a specific port. The HTTP connections can act
 * as pipelines for HTTP requests.</p>
 * <p>It is used as a factory for {@link HttpClientRequest} instances which encapsulate the actual HTTP requests. It is also
 * used as a factory for HTML5 {@link WebSocket websockets}.</p>
 * <p>The client is thread-safe and can be safely shareddata my different event loops.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClient extends NetClientBase {

  private static final Logger log = Logger.getLogger(HttpClientRequest.class);

  private ClientBootstrap bootstrap;
  private NioClientSocketChannelFactory channelFactory;
  private Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap();
  private Handler<Exception> exceptionHandler;
  private int port = 80;
  private String host = "localhost";
  private final ConnectionPool<ClientConnection> pool = new ConnectionPool<ClientConnection>() {
    protected void connect(Handler<ClientConnection> connectHandler, long contextID) {
      internalConnect(connectHandler, contextID);
    }
  };
  private boolean keepAlive = true;

  /**
   * Create an {@code HttpClient} instance
   */
  public HttpClient() {
    super();
  }

  /**
   * Set the exception handler for the {@code HttpClient}
   */
  public void exceptionHandler(Handler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  /**
   * Set the maximum pool size to the value specified by {@code maxConnections}<p>
   * The client will maintain up to {@code maxConnections} HTTP connections in an internal pool<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClient setMaxPoolSize(int maxConnections) {
    pool.setMaxPoolSize(maxConnections);
    return this;
  }

  /**
   * Returns the maximum number of connections in the pool
   */
  public int getMaxPoolSize() {
    return pool.getMaxPoolSize();
  }

  /**
   * If {@code keepAlive} is {@code true} then, after the request has ended the connection will be returned to the pool
   * where it can be used by another request. In this manner, many HTTP requests can be pipe-lined over an HTTP connection.
   * Keep alive connections will not be closed until the {@link #close() close()} method is invoked.<p>
   * If {@code keepAlive} is {@code false} then a new connection will be created for each request and it won't ever go in the pool,
   * the connection will closed after the response has been received. Even with no keep alive, the client will not allow more
   * than {@link #getMaxPoolSize() getMaxPoolSize()} connections to be created at any one time. <p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClient setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setSSL(boolean ssl) {
    return (HttpClient) super.setSSL(ssl);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setKeyStorePath(String path) {
    return (HttpClient) super.setKeyStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setKeyStorePassword(String pwd) {
    return (HttpClient) super.setKeyStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setTrustStorePath(String path) {
    return (HttpClient) super.setTrustStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setTrustStorePassword(String pwd) {
    return (HttpClient) super.setTrustStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setTrustAll(boolean trustAll) {
    return (HttpClient) super.setTrustAll(trustAll);
  }

  /**
   * Set the port that the client will attempt to connect to on the server to {@code port}. The default value is {@code 80}<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClient setPort(int port) {
    this.port = port;
    return this;
  }

  /**
   * Set the host that the client will attempt to connect to, to {@code host}. The default value is {@code localhost}<p>
   *
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public HttpClient setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * This version of the method defaults to the Hybi-10 version of the websockets protocol
   * The connect is done asynchronously and {@code wsConnect} is called back with the result
   */
  public void connectWebsocket(final String uri, final Handler<WebSocket> wsConnect) {
    connectWebsocket(uri, WebSocketVersion.HYBI_17, wsConnect);
  }

  /**
   * Attempt to connect an HTML5 websocket to the specified URI<p>
   * This version of the method allows you to specify the websockets version using the {@code wsVersion parameter}
   * The connect is done asynchronously and {@code wsConnect} is called back with the result
   */
  public void connectWebsocket(final String uri, final WebSocketVersion wsVersion, final Handler<WebSocket> wsConnect) {
    getConnection(new Handler<ClientConnection>() {
      public void handle(final ClientConnection conn) {
        conn.toWebSocket(uri, wsConnect, wsVersion);
      }
    }, Vertx.instance.getContextID());
  }

  /**
   * This is a quick version of the {@link #get(String, org.vertx.java.core.Handler) get()} method where you do not want to do anything with the request
   * before sending.<p>
   * Normally with any of the HTTP methods you create the request then when you are ready to send it you call
   * {@link HttpClientRequest#end()} on it. With this method the request is immediately sent.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public void getNow(String uri, Handler<HttpClientResponse> responseHandler) {
    getNow(uri, null, responseHandler);
  }

  /**
   * This method works in the same manner as {@link #getNow(String, org.vertx.java.core.Handler)},
   * except that it allows you specify a set of {@code headers} that will be sent with the request.
   */
  public void getNow(String uri, Map<String, ? extends Object> headers, Handler<HttpClientResponse> responseHandler) {
    HttpClientRequest req = get(uri, responseHandler);
    if (headers != null) {
      req.putAllHeaders(headers);
    }
    req.end();
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP OPTIONS request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest options(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("OPTIONS", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP GET request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest get(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("GET", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP HEAD request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest head(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("HEAD", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP POST request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest post(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("POST", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP PUT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest put(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("PUT", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP DELETE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest delete(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("DELETE", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP TRACE request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest trace(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("TRACE", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP CONNECT request with the specified {@code uri}.<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest connect(String uri, Handler<HttpClientResponse> responseHandler) {
    return request("CONNECT", uri, responseHandler);
  }

  /**
   * This method returns an {@link HttpClientRequest} instance which represents an HTTP request with the specified {@code uri}. The specific HTTP method
   * (e.g. GET, POST, PUT etc) is specified using the parameter {@code method}<p>
   * When an HTTP response is received from the server the {@code responseHandler} is called passing in the response.
   */
  public HttpClientRequest request(String method, String uri, Handler<HttpClientResponse> responseHandler) {
    final Long cid = Vertx.instance.getContextID();
    if (cid == null) {
      throw new IllegalStateException("Requests must be made from inside an event loop");
    }
    return new HttpClientRequest(this, method, uri, responseHandler, cid, Thread.currentThread());
  }

  /**
   * Close the HTTP client. This will cause any pooled HTTP connections to be closed.
   */
  public void close() {
    pool.close();
    for (ClientConnection conn : connectionMap.values()) {
      conn.internalClose();
    }
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setTcpNoDelay(boolean tcpNoDelay) {
    return (HttpClient) super.setTcpNoDelay(tcpNoDelay);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setSendBufferSize(int size) {
    return (HttpClient) super.setSendBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setReceiveBufferSize(int size) {
    return (HttpClient) super.setReceiveBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setTCPKeepAlive(boolean keepAlive) {
    return (HttpClient) super.setTCPKeepAlive(keepAlive);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setReuseAddress(boolean reuse) {
    return (HttpClient) super.setReuseAddress(reuse);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setSoLinger(boolean linger) {
    return (HttpClient) super.setSoLinger(linger);
  }

  /**
   * {@inheritDoc}
   */
  public HttpClient setTrafficClass(int trafficClass) {
    return (HttpClient) super.setTrafficClass(trafficClass);
  }

  void getConnection(Handler<ClientConnection> handler, long contextID) {
    pool.getConnection(handler, contextID);
  }

  void returnConnection(final ClientConnection conn) {
    if (!conn.keepAlive) {
      //Close it
      conn.internalClose();
    } else {
      pool.returnConnection(conn);
    }
  }

  private void internalConnect(final Handler<ClientConnection> connectHandler, final long contextID) {

    if (bootstrap == null) {
      channelFactory = new NioClientSocketChannelFactory(
          VertxInternal.instance.getAcceptorPool(),
          VertxInternal.instance.getWorkerPool());
      bootstrap = new ClientBootstrap(channelFactory);

      checkSSL();

      bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        public ChannelPipeline getPipeline() throws Exception {
          ChannelPipeline pipeline = Channels.pipeline();
          if (ssl) {
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(true); //We are on the client side of the connection
            pipeline.addLast("ssl", new SslHandler(engine));
          }
          pipeline.addLast("encoder", new HttpRequestEncoder());
          pipeline.addLast("decoder", new SwitchingHttpResponseDecoder());
          pipeline.addLast("handler", new ClientHandler());
          return pipeline;
        }
      });
    }

    //Client connections share context with caller
    channelFactory.setWorker(VertxInternal.instance.getWorkerForContextID(contextID));
    bootstrap.setOptions(connectionOptions);
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (channelFuture.isSuccess()) {

          final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();

          runOnCorrectThread(ch, new Runnable() {
            public void run() {
              final ClientConnection conn = new ClientConnection(HttpClient.this, ch,
                  host + ":" + port, ssl, keepAlive, contextID,
                  Thread.currentThread());
              conn.closedHandler(new SimpleHandler() {
                public void handle() {
                  pool.connectionClosed();
                }
              });
              connectionMap.put(ch, conn);
              VertxInternal.instance.setContextID(contextID);
              connectHandler.handle(conn);
            }
          });
        } else {
          Throwable t = channelFuture.getCause();
          if (t instanceof Exception && exceptionHandler != null) {
            exceptionHandler.handle((Exception) t);
          } else {
            log.error("Unhandled exception", t);
          }
        }
      }
    });
  }

  private class ClientHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ClientConnection conn = connectionMap.remove(ch);
      if (conn != null) {
        runOnCorrectThread(ch, new Runnable() {
          public void run() {
            conn.handleClosed();
          }
        });
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ClientConnection conn = connectionMap.get(ch);
      runOnCorrectThread(ch, new Runnable() {
        public void run() {
          conn.handleInterestedOpsChanged();
        }
      });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ClientConnection conn = connectionMap.get(ch);
      final Throwable t = e.getCause();
      if (conn != null && t instanceof Exception) {
        runOnCorrectThread(ch, new Runnable() {
          public void run() {
            conn.handleException((Exception) t);
          }
        });
      } else {
        log.error("Unhandled exception", t);
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

      Channel ch = e.getChannel();
      ClientConnection conn = connectionMap.get(ch);
      Object msg = e.getMessage();
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) msg;

        conn.handleResponse(response);
        ChannelBuffer content = response.getContent();
        if (content.readable()) {
          conn.handleResponseChunk(new Buffer(content));
        }
        if (!response.isChunked()) {
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
}
