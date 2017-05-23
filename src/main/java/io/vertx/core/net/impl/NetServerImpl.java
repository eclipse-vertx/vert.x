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

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.streams.ReadStream;

import static io.vertx.core.net.impl.VertxHandler.safeBuffer;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetServerImpl extends NetServerBase<NetSocketImpl> implements NetServer {

  private final NetSocketStream connectStream = new NetSocketStream();
  private Handler<NetSocket> handler;
  private Handler<Void> endHandler;

  public NetServerImpl(VertxInternal vertx, NetServerOptions options) {
    super(vertx, options);
  }

  @Override
  public synchronized Handler<NetSocket> connectHandler() {
    return handler;
  }

  @Override
  public synchronized NetServer connectHandler(Handler<NetSocket> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set connectHandler when server is listening");
    }
    this.handler = handler;
    return this;
  }

  @Override
  protected void initChannel(ChannelPipeline pipeline) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    if (sslHelper.isSSL()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (options.getIdleTimeout() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
    }
  }

  @Override
  protected void handleMsgReceived(NetSocketImpl conn, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    conn.handleDataReceived(Buffer.buffer(buf));
  }

  @Override
  protected Object safeObject(Object msg, ByteBufAllocator allocator) {
    if (msg instanceof ByteBuf) {
      return safeBuffer((ByteBuf) msg, allocator);
    }
    return msg;
  }

  @Override
  public NetServer listen(int port, String host) {
    return listen(port, host, null);
  }

  @Override
  public NetServer listen(int port) {
    return listen(port, "0.0.0.0", null);
  }

  @Override
  public NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(port, "0.0.0.0", listenHandler);
  }

  @Override
  public NetServer listen() {
    listen(null);
    return this;
  }

  @Override
  public synchronized NetServer listen(Handler<AsyncResult<NetServer>> listenHandler) {
    return listen(options.getPort(), options.getHost(), listenHandler);
  }

  @Override
  public synchronized NetServerImpl listen(int port, String host, Handler<AsyncResult<NetServer>> listenHandler) {
    listen(handler, port, host, ar -> {
      if (listenHandler != null) {
        listenHandler.handle(ar.map(this));
      }
    });
    return this;
  }

  @Override
  public ReadStream<NetSocket> connectStream() {
    return connectStream;
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> done) {
    if (endHandler != null) {
      Handler<Void> handler = endHandler;
      endHandler = null;
      Handler<AsyncResult<Void>> next = done;
      done = event -> {
        if (event.succeeded()) {
          handler.handle(event.result());
        }
        if (next != null) {
          next.handle(event);
        }
      };
    }
    super.close(done);
  }

  @Override
  protected NetSocketImpl createConnection(VertxInternal vertx, Channel channel, ContextImpl context, SSLHelper helper, TCPMetrics metrics) {
    return new NetSocketImpl(vertx, channel, context, helper, metrics);
  }

  /*
        Needs to be protected using the NetServerImpl monitor as that protects the listening variable
        In practice synchronized overhead should be close to zero assuming most access is from the same thread due
        to biased locks
      */
  private class NetSocketStream implements ReadStream<NetSocket> {

    @Override
    public NetSocketStream handler(Handler<NetSocket> handler) {
      connectHandler(handler);
      return this;
    }

    @Override
    public NetSocketStream pause() {
      pauseAccepting();
      return this;
    }

    @Override
    public NetSocketStream resume() {
      resumeAccepting();
      return this;
    }

    @Override
    public NetSocketStream endHandler(Handler<Void> handler) {
      synchronized (NetServerImpl.this) {
        endHandler = handler;
        return this;
      }
    }

    @Override
    public NetSocketStream exceptionHandler(Handler<Throwable> handler) {
      // Should we use it in the server close exception handler ?
      return this;
    }
  }
}
