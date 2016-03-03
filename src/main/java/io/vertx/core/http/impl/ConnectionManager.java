/*
 * Copyright (c) 2011-2014 The original author or authors
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
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.SSLHelper;

import javax.net.ssl.SSLHandshakeException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConnectionManager {

  static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

  private final Map<Channel, ClientConnection> connectionMap = new ConcurrentHashMap<>();
  private final VertxInternal vertx;
  private final SSLHelper sslHelper;
  private final HttpClientOptions options;
  private final HttpClientImpl client;
  private final boolean keepAlive;
  private final boolean pipelining;
  private final int maxWaitQueueSize;
  private final Map<TargetAddress, ConnQueue> connQueues = new ConcurrentHashMap<>();

  ConnectionManager(HttpClientImpl client) {
    this.client = client;
    this.sslHelper = client.getSslHelper();
    this.options = client.getOptions();
    this.vertx = client.getVertx();
    this.keepAlive = client.getOptions().isKeepAlive();
    this.pipelining = client.getOptions().isPipelining();
    this.maxWaitQueueSize = client.getOptions().getMaxWaitQueueSize();
  }

  public void getConnection(int port, String host, HttpClientRequestImpl req, Handler<HttpClientStream> handler, Handler<Throwable> connectionExceptionHandler,
                            ContextImpl context, BooleanSupplier canceled) {
    if (!keepAlive && pipelining) {
      connectionExceptionHandler.handle(new IllegalStateException("Cannot have pipelining with no keep alive"));
    } else {
      TargetAddress address = new TargetAddress(host, port);
      ConnQueue connQueue = connQueues.get(address);
      if (connQueue == null) {
        connQueue = new ConnQueue(address);
        ConnQueue prev = connQueues.putIfAbsent(address, connQueue);
        if (prev != null) {
          connQueue = prev;
        }
      }
      connQueue.getConnection(req, handler, connectionExceptionHandler, context, canceled);
    }
  }

  public void close() {
    for (ConnQueue queue: connQueues.values()) {
      queue.closeAllConnections();
    }
    connQueues.clear();
    for (ClientConnection conn : connectionMap.values()) {
      conn.close();
    }
  }

  void removeChannel(Channel channel) {
    connectionMap.remove(channel);
  }

  static class TargetAddress {
    final String host;
    final int port;

    TargetAddress(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TargetAddress that = (TargetAddress) o;
      if (port != that.port) return false;
      if (host != null ? !host.equals(that.host) : that.host != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = host != null ? host.hashCode() : 0;
      result = 31 * result + port;
      return result;
    }
  }

  static class Waiter {
    final HttpClientRequestImpl req;
    final Handler<HttpClientStream> handler;
    final Handler<Throwable> connectionExceptionHandler;
    final ContextImpl context;
    final BooleanSupplier canceled;

    private Waiter(HttpClientRequestImpl req, Handler<HttpClientStream> handler, Handler<Throwable> connectionExceptionHandler, ContextImpl context,
                   BooleanSupplier canceled) {
      this.req = req;
      this.handler = handler;
      this.connectionExceptionHandler = connectionExceptionHandler;
      this.context = context;
      this.canceled = canceled;
    }
  }

  public class ConnQueue {

    private final TargetAddress address;
    private final Queue<Waiter> waiters = new ArrayDeque<>();
    private int connCount;
    private Pool pool;

    ConnQueue(TargetAddress address) {
      this.address = address;

      if (options.getProtocolVersion() == HttpVersion.HTTP_2) {
        pool = new Http2Pool(this);
      } else {
        pool = new Http1xPool(this);
      }

    }

    public synchronized void getConnection(HttpClientRequestImpl req, Handler<HttpClientStream> handler, Handler<Throwable> connectionExceptionHandler,
                                           ContextImpl context, BooleanSupplier canceled) {
      boolean served = pool.getConnection(req, handler, context);
      if (!served) {
        if (connCount == pool.maxSockets) {
          // Wait in queue
          if (maxWaitQueueSize < 0 || waiters.size() < maxWaitQueueSize) {
            waiters.add(new Waiter(req, handler, connectionExceptionHandler, context, canceled));
          } else {
            connectionExceptionHandler.handle(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + maxWaitQueueSize));
          }
        } else {
          // Create a new connection
          createNewConnection(req, handler, connectionExceptionHandler, context);
        }
      }
    }

    void closeAllConnections() {
      pool.closeAllConnections();
    }

    private void createNewConnection(
        HttpClientRequestImpl req,
        Handler<HttpClientStream> connectHandler,
        Handler<Throwable> connectionExceptionHandler,
        ContextImpl context) {
      connCount++;
      internalConnect(address.host, address.port, req, conn -> {
        if (conn instanceof ClientConnection) {
          // Moved in 1xPool
        } else {
          // Todo in the other pool ???
        }
        connectHandler.handle(conn);
      }, connectionExceptionHandler, context);
    }

    Waiter getNextWaiter() {
      // See if there are any non-canceled waiters in the queue
      Waiter waiter = waiters.poll();
      while (waiter != null && waiter.canceled.getAsBoolean()) {
        waiter = waiters.poll();
      }
      return waiter;
    }

    // Called if the connection is actually closed OR the connection attempt failed
    public synchronized void connectionClosed() {
      connCount--;
      Waiter waiter = getNextWaiter();
      if (waiter != null) {
        // There's a waiter - so it can have a new connection
        createNewConnection(waiter.req, waiter.handler, waiter.connectionExceptionHandler, waiter.context);
      } else if (connCount == 0) {
        // No waiters and no connections - remove the ConnQueue
        connQueues.remove(address);
      }
    }

    protected void internalConnect(String host, int port, HttpClientRequestImpl req, Handler<HttpClientStream> connectHandler, Handler<Throwable> connectErrorHandler, ContextImpl clientContext) {
      ContextImpl context;
      if (clientContext == null) {
        // Embedded
        context = vertx.getOrCreateContext();
      } else {
        context = clientContext;
      }
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(context.nettyEventLoop());
      bootstrap.channelFactory(new VertxNioSocketChannelFactory());
      sslHelper.validate(vertx);
      bootstrap.handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
          ChannelPipeline pipeline = ch.pipeline();

          if (options.isUseAlpn()) {

            SslHandler sslHandler = sslHelper.createSslHandler(client.getVertx(), true, host, port);
            ch.pipeline().addLast(sslHandler);
            String fallback;
            HttpVersion fallbackVersion = options.getAlpnFallbackProtocolVersion();
            switch (fallbackVersion) {
              case HTTP_1_1:
                fallback = "http/1.1";
                break;
              case HTTP_1_0:
                fallback = "http/1.1";
                break;
              default:
                // Handle this case better
                fallback = "oops";
                break;
            }
            ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler(fallback) {
              @Override
              protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                  http2Connected(ctx, context, port, host, ch, req, connectHandler, connectErrorHandler);
                } else {
                  // Fallback
                  // change the pool to Http1xPool
                  synchronized (ConnQueue.this) {
                    pool = new Http1xPool(ConnQueue.this);
                  }
                  applyHttp1xConnectionOptions(pipeline, context);
                  http1xConnected(fallbackVersion, context, port, host, ch, connectHandler, connectErrorHandler);
                }
              }
            });
          } else {
            if (options.isSsl()) {
              pipeline.addLast("ssl", sslHelper.createSslHandler(vertx, true, host, port));
            }
            applyHttp1xConnectionOptions(pipeline, context);
          }
        }
      });
      applyConnectionOptions(options, bootstrap);
      ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
      future.addListener((ChannelFuture channelFuture) -> {
        Channel ch = channelFuture.channel();
        if (channelFuture.isSuccess()) {
          if (!options.isUseAlpn()) {
            if (options.isSsl()) {
              // TCP connected, so now we must do the SSL handshake

              SslHandler sslHandler = ch.pipeline().get(SslHandler.class);

              io.netty.util.concurrent.Future<Channel> fut = sslHandler.handshakeFuture();
              fut.addListener(fut2 -> {
                if (fut2.isSuccess()) {
                  http1xConnected(options.getProtocolVersion(), context, port, host, ch, connectHandler, connectErrorHandler);
                } else {
                  SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
                  Optional.ofNullable(fut2.cause()).ifPresent(sslException::initCause);
                  connectionFailed(context, ch, connectErrorHandler, sslException);
                }
              });
            } else {
              http1xConnected(options.getProtocolVersion(), context, port, host, ch, connectHandler, connectErrorHandler);
            }
          }
        } else {
          connectionFailed(context, ch, connectErrorHandler, channelFuture.cause());
        }
      });
    }

    private void http1xConnected(HttpVersion version, ContextImpl context, int port, String host, Channel ch, Handler<HttpClientStream> connectHandler,
                                 Handler<Throwable> exceptionHandler) {
      context.executeFromIO(() ->
          ((Http1xPool)pool).createConn(version, context, port, host, ch, connectHandler, exceptionHandler)
      );
    }

    private void http2Connected(ChannelHandlerContext handlerCtx, ContextImpl context, int port, String host, Channel ch, HttpClientRequestImpl req, Handler<HttpClientStream> connectHandler,
                                Handler<Throwable> exceptionHandler) {
      context.executeFromIO(() ->
          ((Http2Pool)pool).createConn(handlerCtx, context, port, host, ch, req, connectHandler, exceptionHandler)
      );
    }

    private void connectionFailed(ContextImpl context, Channel ch, Handler<Throwable> connectionExceptionHandler,
                                  Throwable t) {
      // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
      // If that doesn't exist just log it
      Handler<Throwable> exHandler =
          connectionExceptionHandler == null ? log::error : connectionExceptionHandler;

      context.executeFromIO(() -> {
        connectionClosed();
        try {
          ch.close();
        } catch (Exception ignore) {
        }
        if (exHandler != null) {
          exHandler.handle(t);
        } else {
          log.error(t);
        }
      });
    }
  }

  static abstract class Pool {

    public final int maxSockets;

    public Pool(int maxSockets) {
      this.maxSockets = maxSockets;
    }

    abstract boolean getConnection(HttpClientRequestImpl req, Handler<HttpClientStream> handler, ContextImpl context);

    abstract void closeAllConnections();
  }

  // Locks on the queue to keep a single lock
  public class Http1xPool extends Pool {

    final ConnQueue queue;
    private final Set<ClientConnection> allConnections = new HashSet<>();
    private final Queue<ClientConnection> availableConnections = new ArrayDeque<>();

    public Http1xPool(ConnQueue queue) {
      super(client.getOptions().getMaxPoolSize());
      this.queue = queue;
    }

    public boolean getConnection(HttpClientRequestImpl req, Handler<HttpClientStream> handler, ContextImpl context) {
      ClientConnection conn = availableConnections.poll();
      if (conn != null && !conn.isClosed()) {
        if (context == null) {
          context = conn.getContext();
        } else if (context != conn.getContext()) {
          log.warn("Reusing a connection with a different context: an HttpClient is probably shared between different Verticles");
        }
        context.runOnContext(v -> handler.handle(conn));
        return true;
      } else {
        return false;
      }
    }

    // Called when the request has ended
    public void requestEnded(ClientConnection conn) {
      synchronized (queue) {
        if (pipelining) {
          // Maybe the connection can be reused
          Waiter waiter = queue.getNextWaiter();
          if (waiter != null) {
            Context context = waiter.context;
            if (context == null) {
              context = conn.getContext();
            }
            context.runOnContext(v -> waiter.handler.handle(conn));
          }
        }
      }
    }

    // Called when the response has ended
    public synchronized void responseEnded(ClientConnection conn, boolean close) {
      synchronized (queue) {
        if ((pipelining || keepAlive) && !close) {
          if (conn.getCurrentRequest() == null) {
            Waiter waiter = queue.getNextWaiter();
            if (waiter != null) {
              Context context = waiter.context;
              if (context == null) {
                context = conn.getContext();
              }
              context.runOnContext(v -> waiter.handler.handle(conn));
            } else if (conn.getOutstandingRequestCount() == 0) {
              // Return to set of available from here to not return it several times
              availableConnections.add(conn);
            }
          }
        } else {
          // Close it now
          conn.close();
        }
      }
    }

    private void createConn(HttpVersion version, ContextImpl context, int port, String host, Channel ch, Handler<HttpClientStream> connectHandler,
                            Handler<Throwable> exceptionHandler) {
      ClientConnection conn = new ClientConnection(version, ConnectionManager.this, vertx, client, exceptionHandler, ch,
          options.isSsl(), host, port, context, this, client.metrics);
      conn.closeHandler(v -> {
        // The connection has been closed - tell the pool about it, this allows the pool to create more
        // connections. Note the pool doesn't actually remove the connection, when the next person to get a connection
        // gets the closed on, they will check if it's closed and if so get another one.
        connectionClosed(conn);
      });
      synchronized (queue) {
        allConnections.add(conn);
      }
      connectionMap.put(ch, conn);
      connectHandler.handle(conn);
    }

    // Called if the connection is actually closed, OR the connection attempt failed - in the latter case
    // conn will be null
    public synchronized void connectionClosed(ClientConnection conn) {
      synchronized (queue) {
        allConnections.remove(conn);
        availableConnections.remove(conn);
        queue.connectionClosed();
      }
    }

    void closeAllConnections() {
      Set<ClientConnection> copy;
      synchronized (this) {
        copy = new HashSet<>(allConnections);
        allConnections.clear();
      }
      // Close outside sync block to avoid deadlock
      for (ClientConnection conn: copy) {
        try {
          conn.close();
        } catch (Throwable t) {
          log.error("Failed to close connection", t);
        }
      }
    }
  }

  private class ClientHandler extends VertxHttpHandler<ClientConnection> {
    private boolean closeFrameSent;
    private ContextImpl context;

    public ClientHandler(ContextImpl context) {
      super(ConnectionManager.this.connectionMap);
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
          case PONG:
            // Just ignore it
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

  void applyConnectionOptions(HttpClientOptions options, Bootstrap bootstrap) {
    bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    if (options.getSendBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    if (options.getSoLinger() != -1) {
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    }
    if (options.getTrafficClass() != -1) {
      bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());
    bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
  }

  void applyHttp1xConnectionOptions(ChannelPipeline pipeline, ContextImpl context) {
    pipeline.addLast("codec", new HttpClientCodec(4096, 8192, options.getMaxChunkSize(), false, false));
    if (options.isTryUseCompression()) {
      pipeline.addLast("inflater", new HttpContentDecompressor(true));
    }
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
    pipeline.addLast("handler", new ClientHandler(context));
  }
}
