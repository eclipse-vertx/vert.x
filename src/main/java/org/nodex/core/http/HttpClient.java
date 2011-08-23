/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.http;

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
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.ssl.SslHandler;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.Nodex;
import org.nodex.core.NodexInternal;
import org.nodex.core.SSLBase;
import org.nodex.core.ThreadSourceUtils;
import org.nodex.core.buffer.Buffer;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpClient extends SSLBase {

  private ClientBootstrap bootstrap;
  private NioClientSocketChannelFactory channelFactory;
  private Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap();
  private boolean keepAlive = true;
  private ExceptionHandler exceptionHandler;
  private int port = 80;
  private String host = "localhost";
  private final Queue<ClientConnection> available = new ConcurrentLinkedQueue<ClientConnection>();
  private int maxPoolSize = 1;
  private final AtomicInteger connectionCount = new AtomicInteger(0);
  private final Queue<Waiter> waiters = new ConcurrentLinkedQueue<Waiter>();

  public HttpClient() {
  }

  public void exceptionHandler(ExceptionHandler handler) {
    this.exceptionHandler = handler;
  }

  public HttpClient setMaxPoolSize(int maxConnections) {
    this.maxPoolSize = maxConnections;
    return this;
  }

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public HttpClient setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  public HttpClient setSSL(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public HttpClient setKeyStorePath(String path) {
    this.keyStorePath = path;
    return this;
  }

  public HttpClient setKeyStorePassword(String pwd) {
    this.keyStorePassword = pwd;
    return this;
  }
  public HttpClient setTrustStorePath(String path) {
    this.trustStorePath = path;
    return this;
  }

  public HttpClient setTrustStorePassword(String pwd) {
    this.trustStorePassword = pwd;
    return this;
  }

  public HttpClient setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  public HttpClient setPort(int port) {
    this.port = port;
    return this;
  }

  public HttpClient setHost(String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public String getHost() {
    return host;
  }

  public void connectWebsocket(final String uri, final WebsocketConnectHandler wsConnect) {
    connectWebsocket(uri, null, wsConnect);
  }

  public void connectWebsocket(final String uri, final Map<String, ? extends Object> headers,
                               final WebsocketConnectHandler wsConnect) {
    getConnection(new ClientConnectHandler() {
      public void onConnect(final ClientConnection conn) {
        conn.toWebSocket(uri, headers, wsConnect);
      }
    }, Nodex.instance.getContextID());
  }

  // Quick get method when there's no body and it doesn't require an end
  public void getNow(String uri, HttpResponseHandler responseHandler) {
    getNow(uri, null, responseHandler);
  }

  public void getNow(String uri, Map<String, ? extends Object> headers, HttpResponseHandler responseHandler) {
    HttpClientRequest req = get(uri, responseHandler);
    if (headers != null) {
      req.putAllHeaders(headers);
    }
    req.end();
  }

  public HttpClientRequest options(String uri, HttpResponseHandler responseHandler) {
    return request("OPTIONS", uri, responseHandler);
  }

  public HttpClientRequest get(String uri, HttpResponseHandler responseHandler) {
    return request("GET", uri, responseHandler);
  }

  public HttpClientRequest head(String uri, HttpResponseHandler responseHandler) {
    return request("HEAD", uri, responseHandler);
  }

  public HttpClientRequest post(String uri, HttpResponseHandler responseHandler) {
    return request("POST", uri, responseHandler);
  }

  public HttpClientRequest put(String uri, HttpResponseHandler responseHandler) {
    return request("PUT", uri, responseHandler);
  }

  public HttpClientRequest delete(String uri, HttpResponseHandler responseHandler) {
    return request("DELETE", uri, responseHandler);
  }

  public HttpClientRequest trace(String uri, HttpResponseHandler responseHandler) {
    return request("TRACE", uri, responseHandler);
  }

  public HttpClientRequest connect(String uri, HttpResponseHandler responseHandler) {
    return request("CONNECT", uri, responseHandler);
  }

  public HttpClientRequest request(String method, String uri, HttpResponseHandler responseHandler) {
    final Long cid = Nodex.instance.getContextID();
    if (cid == null) {
      throw new IllegalStateException("Requests must be made from inside an event loop");
    }
    return new HttpClientRequest(this, method, uri, responseHandler, cid, Thread.currentThread());
  }

  public void close() {
    for (ClientConnection conn : connectionMap.values()) {
      conn.internalClose();
    }
    available.clear();
    if (!waiters.isEmpty()) {
      System.out.println("Warning: Closing HTTP client, but there are " + waiters.size() + " waiting for connections");
    }
    waiters.clear();
  }

  //TODO FIXME - heavyweight synchronization for now FIXME
  //This will be a contention point
  //Need to be improved

  synchronized void getConnection(ClientConnectHandler handler, long contextID) {
    ClientConnection conn = available.poll();
    if (conn != null) {
      handler.onConnect(conn);
    } else {
      if (connectionCount.get() < maxPoolSize) {
        if (connectionCount.incrementAndGet() <= maxPoolSize) {
          //Create new connection
          connect(handler, contextID);
          return;
        } else {
          connectionCount.decrementAndGet();
        }
      }
      // Add to waiters
      waiters.add(new Waiter(handler, contextID));
    }
  }

  synchronized void returnConnection(final ClientConnection conn) {
    if (!conn.keepAlive) {
      //Just close it
      conn.internalClose();
      if (connectionCount.decrementAndGet() < maxPoolSize) {
        //Now the connection count has come down, maybe there is another waiter that can
        //create a new connection
        Waiter waiter = waiters.poll();
        if (waiter != null) {
          getConnection(waiter.handler, waiter.contextID);
        }
      }
    } else {
      //Return it to the pool
      final Waiter waiter = waiters.poll();

      if (waiter != null) {
        NodexInternal.instance.executeOnContext(waiter.contextID, new Runnable() {
          public void run() {
            NodexInternal.instance.setContextID(waiter.contextID);
            waiter.handler.onConnect(conn);
          }
        });
      } else {
        available.add(conn);
      }
    }
  }

  private void connect(final ClientConnectHandler connectHandler, final long contextID) {

    if (bootstrap == null) {
      channelFactory = new NioClientSocketChannelFactory(
              NodexInternal.instance.getAcceptorPool(),
              NodexInternal.instance.getWorkerPool());
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
          pipeline.addLast("decoder", new HttpResponseDecoder());
          pipeline.addLast("handler", new ClientHandler());
          return pipeline;
        }
      });
    }

    //Client connections share context with caller
    channelFactory.setWorker(NodexInternal.instance.getWorkerForContextID(contextID));

    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (channelFuture.isSuccess()) {

          final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();

          ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
            public void run() {
              ClientConnection conn = new ClientConnection(HttpClient.this, ch, keepAlive, host + ":" + port, ssl, contextID,
                  Thread.currentThread());
              connectionMap.put(ch, conn);
              NodexInternal.instance.setContextID(contextID);
              connectHandler.onConnect(conn);
            }
          });
        } else {
          Throwable t = channelFuture.getCause();
          if (t instanceof Exception && exceptionHandler != null) {
            exceptionHandler.onException((Exception)t);
          } else {
            t.printStackTrace(System.err);
          }
        }
      }
    });
  }

  private static class Waiter {
    final ClientConnectHandler handler;
    final long contextID;

    private Waiter(ClientConnectHandler handler, long contextID) {
      this.handler = handler;
      this.contextID = contextID;
    }
  }

  private class ClientHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final ClientConnection conn = connectionMap.remove(ch);
      if (conn != null) {
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
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
      ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
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
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
          public void run() {
            conn.handleException((Exception) t);
          }
        });
      } else {
        t.printStackTrace(System.err);
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
