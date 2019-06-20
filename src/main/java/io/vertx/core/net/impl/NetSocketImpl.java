/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NetSocketInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.streams.impl.InboundBuffer;

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
  private final SSLHelper helper;
  private final SocketAddress remoteAddress;
  private final TCPMetrics metrics;
  private final InboundBuffer<Object> pending;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private MessageConsumer registration;
  private Handler<Object> messageHandler;
  private boolean closed;

  public NetSocketImpl(VertxInternal vertx, ChannelHandlerContext channel, ContextInternal context,
                       SSLHelper helper, TCPMetrics metrics) {
    this(vertx, channel, null, context, helper, metrics);
  }

  public NetSocketImpl(VertxInternal vertx, ChannelHandlerContext channel, SocketAddress remoteAddress, ContextInternal context,
                       SSLHelper helper, TCPMetrics metrics) {
    super(vertx, channel, context);
    this.helper = helper;
    this.writeHandlerID = "__vertx.net." + UUID.randomUUID().toString();
    this.remoteAddress = remoteAddress;
    this.metrics = metrics;
    this.messageHandler = NULL_MSG_HANDLER;
    pending = new InboundBuffer<>(context);
    pending.drainHandler(v -> doResume());
    pending.handler(obj -> {
      if (obj == InboundBuffer.END_SENTINEL) {
        Handler<Void> handler = endHandler();
        if (handler != null) {
          handler.handle(null);
        }
      } else {
        Handler<Object> handler = messageHandler();
        if (handler != null) {
          handler.handle(obj);
        }
      }
    });
  }

  synchronized void registerEventBusHandler() {
    Handler<Message<Buffer>> writeHandler = msg -> write(msg.body());
    registration = vertx.eventBus().<Buffer>localConsumer(writeHandlerID).handler(writeHandler);
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
  public synchronized NetSocketInternal writeMessage(Object message) {
    if (closed) {
      throw new IllegalStateException("Socket is closed");
    }
    writeToChannel(message);
    return this;
  }

  @Override
  public NetSocketInternal writeMessage(Object message, Handler<AsyncResult<Void>> handler) {
    writeToChannel(message, toPromise(handler));
    return this;
  }

  @Override
  public Future<Void> write(Buffer data) {
    Promise<Void> promise = Promise.promise();
    write(data, promise);
    return promise.future();
  }

  @Override
  public void write(String str, Handler<AsyncResult<Void>> handler) {
    write(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8), handler);
  }

  @Override
  public Future<Void> write(String str) {
    Promise<Void> promise = Promise.promise();
    write(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8), promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(String str, String enc) {
    Promise<Void> promise = Promise.promise();
    write(Unpooled.copiedBuffer(str, Charset.forName(enc)), promise);
    return promise.future();
  }

  @Override
  public void write(String str, String enc, Handler<AsyncResult<Void>> handler) {
    Charset cs = enc != null ? Charset.forName(enc) : CharsetUtil.UTF_8;
    write(Unpooled.copiedBuffer(str, cs), handler);
  }

  @Override
  public void write(Buffer message, Handler<AsyncResult<Void>> handler) {
    write(message.getByteBuf(), handler);
  }

  private void write(ByteBuf buff, Handler<AsyncResult<Void>> handler) {
    reportBytesWritten(buff.readableBytes());
    writeMessage(buff, handler);
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

  private synchronized Handler<Object> messageHandler() {
    return messageHandler;
  }

  @Override
  public synchronized NetSocketInternal messageHandler(Handler<Object> handler) {
    messageHandler = handler;
    return this;
  }

  @Override
  public synchronized NetSocket pause() {
    pending.pause();
    return this;
  }

  @Override
  public NetSocket fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  @Override
  public synchronized NetSocket resume() {
    return fetch(Long.MAX_VALUE);
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

  private synchronized Handler<Void> endHandler() {
    return endHandler;
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

  public NetSocketImpl exceptionHandler(Handler<Throwable> handler) {
    return (NetSocketImpl) super.exceptionHandler(handler);
  }

  @Override
  public NetSocketImpl closeHandler(Handler<Void> handler) {
    return (NetSocketImpl) super.closeHandler(handler);
  }

  @Override
  public NetSocket upgradeToSsl(Handler<AsyncResult<Void>> handler) {
    return upgradeToSsl(null, handler);
  }

  @Override
  public NetSocket upgradeToSsl(String serverName, Handler<AsyncResult<Void>> handler) {
    ChannelOutboundHandler sslHandler = (ChannelOutboundHandler) chctx.pipeline().get("ssl");
    if (sslHandler == null) {
      chctx.pipeline().addFirst("handshaker", new SslHandshakeCompletionHandler(ar -> {
        if (handler != null) {
          handler.handle(ar.mapEmpty());
        }
      }));
      if (remoteAddress != null) {
        sslHandler = new SslHandler(helper.createEngine(vertx, remoteAddress, serverName));
        ((SslHandler) sslHandler).setHandshakeTimeout(helper.getSslHandshakeTimeout(), helper.getSslHandshakeTimeoutUnit());
      } else {
        if (helper.isSNI()) {
          sslHandler = new SniHandler(helper.serverNameMapper(vertx));
        } else {
          sslHandler = new SslHandler(helper.createEngine(vertx));
          ((SslHandler) sslHandler).setHandshakeTimeout(helper.getSslHandshakeTimeout(), helper.getSslHandshakeTimeoutUnit());
        }
      }
      chctx.pipeline().addFirst("ssl", sslHandler);
    }
    return this;
  }

  @Override
  protected void handleInterestedOpsChanged() {
    context.dispatch(v -> callDrainHandler());
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    close(handler);
  }

  @Override
  public Future<Void> end() {
    return close();
  }

  @Override
  protected void handleClosed() {
    MessageConsumer consumer;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      consumer = registration;
      registration = null;
    }
    pending.write(InboundBuffer.END_SENTINEL);
    super.handleClosed();
    if (consumer != null) {
      consumer.unregister();
    }
  }

  public void handleMessage(Object msg) {
    context.dispatch(msg, o -> {
      if (!pending.write(msg)) {
        doPause();
      }
    });
  }

  private class DataMessageHandler implements Handler<Object> {

    private final Handler<Buffer> dataHandler;
    private final ByteBufAllocator allocator;

    DataMessageHandler(ByteBufAllocator allocator, Handler<Buffer> dataHandler) {
      this.allocator = allocator;
      this.dataHandler = dataHandler;
    }

    @Override
    public void handle(Object event) {
      if (event instanceof ByteBuf) {
        ByteBuf byteBuf = (ByteBuf) event;
        byteBuf = VertxHandler.safeBuffer(byteBuf, allocator);
        Buffer data = Buffer.buffer(byteBuf);
        reportBytesRead(data.length());
        dataHandler.handle(data);
      }
    }
  }

  private synchronized void callDrainHandler() {
    if (drainHandler != null) {
      if (!writeQueueFull()) {
        drainHandler.handle(null);
      }
    }
  }
}

