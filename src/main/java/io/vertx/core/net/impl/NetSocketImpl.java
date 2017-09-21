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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.NetSocketInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
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
public class NetSocketImpl extends ConnectionBase implements NetSocketInternal {

  private static final Handler<Object> NULL_MSG_HANDLER = event -> {
    if (event instanceof ByteBuf) {
      ByteBuf byteBuf = (ByteBuf) event;
      byteBuf.release();
    }
  };

  private static final Logger log = LoggerFactory.getLogger(NetSocketImpl.class);

  private final String writeHandlerID;
  private final MessageConsumer registration;
  private final SSLHelper helper;
  private final SocketAddress remoteAddress;
  private final TCPMetrics metrics;
  private Handler<Object> messageHandler = NULL_MSG_HANDLER;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private Buffer pendingData;
  private boolean paused = false;

  public NetSocketImpl(VertxInternal vertx, ChannelHandlerContext channel, ContextImpl context,
                       SSLHelper helper, TCPMetrics metrics) {
    this(vertx, channel, null, context, helper, metrics);
  }

  public NetSocketImpl(VertxInternal vertx, ChannelHandlerContext channel, SocketAddress remoteAddress, ContextImpl context,
                       SSLHelper helper, TCPMetrics metrics) {
    super(vertx, channel, context);
    this.helper = helper;
    this.writeHandlerID = UUID.randomUUID().toString();
    this.remoteAddress = remoteAddress;
    this.metrics = metrics;
    Handler<Message<Buffer>> writeHandler = msg -> write(msg.body());
    registration = vertx.eventBus().<Buffer>localConsumer(writeHandlerID).handler(writeHandler);
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return chctx;
  }

  @Override
  public TCPMetrics metrics() {
    return metrics;
  }

  @Override
  public String writeHandlerID() {
    return writeHandlerID;
  }

  @Override
  public NetSocketInternal writeMessage(Object message) {
    super.writeToChannel(message);
    return this;
  }

  @Override
  public NetSocketInternal writeMessage(Object message, Handler<AsyncResult<Void>> handler) {
    ChannelPromise promise = chctx.newPromise();
    super.writeToChannel(message, promise);
    promise.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          handler.handle(Future.succeededFuture());
        } else {
          handler.handle(Future.failedFuture(future.cause()));
        }
      }
    });
    return this;
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
    if (dataHandler != null) {
      messageHandler(new DataMessageHandler(channelHandlerContext().alloc(), dataHandler));
    } else {
      messageHandler(null);
    }
    return this;
  }

  @Override
  public synchronized NetSocketInternal messageHandler(Handler<Object> handler) {
    if (handler != null) {
      messageHandler = handler;
    } else {
      messageHandler = NULL_MSG_HANDLER;
    }
    return this;
  }

  @Override
  public synchronized NetSocket pause() {
    if (!paused) {
      paused = true;
      doPause();
    }
    return this;
  }

  @Override
  public synchronized NetSocket resume() {
    if (paused) {
      paused = false;
      if (pendingData != null) {
        // Send empty buffer to trigger sending of pending data
        context.runOnContext(v -> handleMessageReceived(Unpooled.EMPTY_BUFFER));
      }
      doResume();
    }
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
  public NetSocket sendFile(String filename, long offset, long length) {
    return sendFile(filename, offset, length, null);
  }

  @Override
  public NetSocket sendFile(String filename, long offset, long length, final Handler<AsyncResult<Void>> resultHandler) {
    File f = vertx.resolveFile(filename);
    if (f.isDirectory()) {
      throw new IllegalArgumentException("filename must point to a file and not to a directory");
    }
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile(f, "r");
      ChannelFuture future = super.sendFile(raf, Math.min(offset, f.length()), Math.min(length, f.length() - offset));
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
      try {
        if (raf != null) {
          raf.close();
        }
      } catch (IOException ignore) {
      }
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
  public NetSocketImpl exceptionHandler(Handler<Throwable> handler) {
    return (NetSocketImpl) super.exceptionHandler(handler);
  }

  @Override
  public NetSocketImpl closeHandler(Handler<Void> handler) {
    return (NetSocketImpl) super.closeHandler(handler);
  }

  @Override
  public synchronized void close() {
    // Close after all data is written
    chctx.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    chctx.flush();
  }

  @Override
  public NetSocket upgradeToSsl(Handler<Void> handler) {
    return upgradeToSsl(null, handler);
  }

  @Override
  public NetSocket upgradeToSsl(String serverName, Handler<Void> handler) {
    ChannelOutboundHandler sslHandler = (ChannelOutboundHandler) chctx.pipeline().get("ssl");
    if (sslHandler == null) {
      if (remoteAddress != null) {
        sslHandler = new SslHandler(helper.createEngine(vertx, remoteAddress, serverName));
      } else {
        if (helper.isSNI()) {
          sslHandler = new VertxSniHandler(helper, vertx);
        } else {
          sslHandler = new SslHandler(helper.createEngine(vertx));
        }
      }
      chctx.pipeline().addFirst("ssl", sslHandler);
    }
    io.netty.util.concurrent.Future<Channel> handshakeFuture;
    if (sslHandler instanceof SslHandler) {
      handshakeFuture = ((SslHandler) sslHandler).handshakeFuture();
    } else {
      handshakeFuture = ((VertxSniHandler) sslHandler).handshakeFuture();
    }
    handshakeFuture.addListener(future -> context.executeFromIO(() -> {
      if (future.isSuccess()) {
        handler.handle(null);
      } else {
        log.error(future.cause());
      }
    }));
    return this;
  }

  @Override
  protected synchronized void handleInterestedOpsChanged() {
    checkContext();
    callDrainHandler();
  }

  @Override
  public void end() {
    close();
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

  public synchronized void handleMessageReceived(Object msg) {
    checkContext();
    if (messageHandler != null) {
      messageHandler.handle(msg);
    }
  }

  private class DataMessageHandler implements Handler<Object> {

    private final ByteBufAllocator allocator;
    private final Handler<Buffer> dataHandler;

    public DataMessageHandler(ByteBufAllocator allocator, Handler<Buffer> dataHandler) {
      this.dataHandler = dataHandler;
      this.allocator = allocator;
    }

    @Override
    public void handle(Object event) {
      if (event instanceof ByteBuf) {
        ByteBuf byteBuf = (ByteBuf) event;
        byteBuf = VertxHandler.safeBuffer(byteBuf, allocator);
        Buffer data = Buffer.buffer(byteBuf);
        reportBytesRead(data.length());
        if (paused) {
          if (pendingData == null) {
            pendingData = data.copy();
          } else {
            pendingData.appendBuffer(data);
          }
          return;
        }
        if (pendingData != null) {
          data = pendingData.appendBuffer(data);
          pendingData = null;
        }
        dataHandler.handle(data);
      }
    }
  }

  private void write(ByteBuf buff) {
    reportBytesWritten(buff.readableBytes());
    super.writeToChannel(buff);
  }

  private synchronized void callDrainHandler() {
    if (drainHandler != null) {
      if (!writeQueueFull()) {
        drainHandler.handle(null);
      }
    }
  }
}

