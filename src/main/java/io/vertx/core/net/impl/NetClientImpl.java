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
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;

import static io.vertx.core.net.impl.VertxHandler.safeBuffer;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClientImpl extends NetClientBase<NetSocketImpl> implements NetClient, MetricsProvider {

  private final int idleTimeout;
  private final boolean logEnabled;

  public NetClientImpl(VertxInternal vertx, NetClientOptions options) {
    this(vertx, options, true);
  }

  public NetClientImpl(VertxInternal vertx, NetClientOptions options, boolean useCreatingContext) {
    super(vertx, options, useCreatingContext);
    logEnabled = options.getLogActivity();
    idleTimeout = options.getIdleTimeout();
  }

  public synchronized NetClient connect(int port, String host, Handler<AsyncResult<NetSocket>> connectHandler) {
    connect(port, host, null, connectHandler);
    return this;
  }

  @Override
  public NetClient connect(int port, String host, String serverName, Handler<AsyncResult<NetSocket>> connectHandler) {
    doConnect(port, host, serverName, connectHandler != null ? ar -> connectHandler.handle(ar.map(s -> (NetSocket) s)) : null);
    return this;
  }

  @Override
  protected Object safeObject(Object msg, ByteBufAllocator allocator) {
    if (msg instanceof ByteBuf) {
      return safeBuffer((ByteBuf) msg, allocator);
    }
    return msg;
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
    if (idleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, idleTimeout));
    }
  }

  @Override
  protected NetSocketImpl createConnection(VertxInternal vertx, Channel channel, String host, int port, ContextImpl context, SSLHelper helper, TCPMetrics metrics) {
    return new NetSocketImpl(vertx, channel, host, port, context, helper, metrics);
  }

  @Override
  protected void handleMsgReceived(NetSocketImpl conn, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    conn.handleDataReceived(Buffer.buffer(buf));
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

