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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VoidHandler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/**
 *
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetSocketImpl extends ConnectionBase implements NetSocket {

  private static final Logger log = LoggerFactory.getLogger(NetSocketImpl.class);

  private final String writeHandlerID;
  private final MessageConsumer registration;
  private final SSLHelper helper;
  private final boolean client;
  private final Object metric;
  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private Queue<Buffer> pendingData;
  private boolean paused = false;
  private ChannelFuture writeFuture;

  public NetSocketImpl(VertxInternal vertx, Channel channel, ContextImpl context, SSLHelper helper, boolean client, TCPMetrics metrics) {
    super(vertx, channel, context, metrics);
    this.helper = helper;
    this.client = client;
    this.writeHandlerID = UUID.randomUUID().toString();
    this.metric = metrics.connected(remoteAddress());
    Handler<Message<Buffer>> writeHandler = msg -> write(msg.body());
    registration = vertx.eventBus().<Buffer>localConsumer(writeHandlerID).handler(writeHandler);
  }

  @Override
  protected Object metric() {
    return metric;
  }

  @Override
  public String writeHandlerID() {
    return writeHandlerID;
  }

  @Override
  public NetSocket write(Buffer data) {
    ByteBuf buf = data.getByteBuf();
    write(buf);
    return this;
  }

  @Override
  public NetSocket write(String str) {
    write(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  @Override
  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      write(Unpooled.copiedBuffer(str, Charset.forName(enc)));
    }
    return this;
  }

  @Override
  public synchronized NetSocket handler(Handler<Buffer> dataHandler) {
    this.dataHandler = dataHandler;
    return this;
  }

  @Override
  public synchronized NetSocket pause() {
    paused = true;
    doPause();
    return this;
  }

  @Override
  public synchronized NetSocket resume() {
    if (!paused) {
      return this;
    }
    paused = false;
    if (pendingData != null) {
      for (;;) {
        final Buffer buf = pendingData.poll();
        if (buf == null) {
          break;
        }
        context.runOnContext(new VoidHandler() {
          @Override
          protected void handle() {
            handleDataReceived(buf);
          }
        });
      }
    }
    doResume();
    return this;
  }

  @Override
  public NetSocket setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return isNotWritable();
  }

  @Override
  public synchronized NetSocket endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public synchronized NetSocket drainHandler(Handler<Void> drainHandler) {
    this.drainHandler = drainHandler;
    vertx.runOnContext(v -> callDrainHandler()); //If the channel is already drained, we want to call it immediately
    return this;
  }

  @Override
  public NetSocket sendFile(String filename) {
    return sendFile(filename, null);
  }

  @Override
  public NetSocket sendFile(String filename, final Handler<AsyncResult<Void>> resultHandler) {
    File f = vertx.resolveFile(filename);
    if (f.isDirectory()) {
      throw new IllegalArgumentException("filename must point to a file and not to a directory");
    }
    try {
      RandomAccessFile raf = new RandomAccessFile(f, "r");
      ChannelFuture future = super.sendFile(raf, f.length());
      if (resultHandler != null) {
        future.addListener(fut -> {
          final AsyncResult<Void> res;
          if (future.isSuccess()) {
            res = Future.succeededFuture();
          } else {
            res = Future.failedFuture(future.cause());
          }
          vertx.runOnContext(v -> resultHandler.handle(res));
        });
      }
    } catch (IOException e) {
      if (resultHandler != null) {
        vertx.runOnContext(v -> resultHandler.handle(Future.failedFuture(e)));
      } else {
        log.error("Failed to send file", e);
      }
    }
    return this;
  }

  @Override
  public SocketAddress remoteAddress() {
    return super.remoteAddress();
  }

  public SocketAddress localAddress() {
    return super.localAddress();
  }

  @Override
  public synchronized NetSocket exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized NetSocket closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
    return this;
  }

  @Override
  public synchronized void close() {
    if (writeFuture != null) {
      // Close after all data is written
      writeFuture.addListener(ChannelFutureListener.CLOSE);
      channel.flush();
    } else {
      super.close();
    }
  }

  @Override
  public synchronized NetSocket upgradeToSsl(final Handler<Void> handler) {
    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
    if (sslHandler == null) {
      sslHandler = helper.createSslHandler(vertx, client);
      channel.pipeline().addFirst(sslHandler);
    }
    sslHandler.handshakeFuture().addListener(future -> {
      context.executeFromIO(() -> {
        if (future.isSuccess()) {
          handler.handle(null);
        } else {
          log.error(future.cause());
        }
      });
    });
    return this;
  }

  @Override
  public boolean isSsl() {
    return channel.pipeline().get(SslHandler.class) != null;
  }

  @Override
  protected synchronized void handleInterestedOpsChanged() {
    checkContext();
    callDrainHandler();
  }

  @Override
  protected synchronized void handleClosed() {
    checkContext();
    if (endHandler != null) {
      endHandler.handle(null);
    }
    super.handleClosed();
    if (vertx.eventBus() != null) {
      registration.unregister();
    }
  }

  synchronized void handleDataReceived(Buffer data) {
    checkContext();
    if (paused) {
      if (pendingData == null) {
        pendingData = new ArrayDeque<>();
      }
      pendingData.add(data);
      return;
    }
    reportBytesRead(data.length());
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }

  private void write(ByteBuf buff) {
    reportBytesWritten(buff.readableBytes());
    writeFuture = super.writeToChannel(buff);
  }

  private synchronized void callDrainHandler() {
    if (drainHandler != null) {
      if (!writeQueueFull()) {
        drainHandler.handle(null);
      }
    }
  }

}

