/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.impl.ContextInternal;
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

  private static final Handler<Object> DEFAULT_MSG_HANDLER = new InvalidMessageHandler();
  private static final Logger log = LoggerFactory.getLogger(NetSocketImpl.class);

  private final String writeHandlerID;
  private final SSLHelper helper;
  private final SocketAddress remoteAddress;
  private final TCPMetrics metrics;
  private final InboundBuffer<Object> pending;
  private final String negotiatedApplicationLayerProtocol;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private MessageConsumer registration;
  private Handler<Object> messageHandler;

  public NetSocketImpl(ContextInternal context, ChannelHandlerContext channel, SSLHelper helper, TCPMetrics metrics) {
    this(context, channel, null, helper, metrics, null);
  }

  public NetSocketImpl(ContextInternal context,
                       ChannelHandlerContext channel,
                       SocketAddress remoteAddress,
                       SSLHelper helper,
                       TCPMetrics metrics,
                       String negotiatedApplicationLayerProtocol) {
    super(context, channel);
    this.helper = helper;
    this.writeHandlerID = "__vertx.net." + UUID.randomUUID().toString();
    this.remoteAddress = remoteAddress;
    this.metrics = metrics;
    this.messageHandler = DEFAULT_MSG_HANDLER;
    this.negotiatedApplicationLayerProtocol = negotiatedApplicationLayerProtocol;
    pending = new InboundBuffer<>(context);
    pending.drainHandler(v -> doResume());
    pending.exceptionHandler(context::reportException);
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

  void registerEventBusHandler() {
    Handler<Message<Buffer>> writeHandler = msg -> write(msg.body());
    registration = vertx.eventBus().<Buffer>localConsumer(writeHandlerID).handler(writeHandler);
  }

  void unregisterEventBusHandler() {
    if (registration != null) {
      MessageConsumer consumer = registration;
      registration = null;
      consumer.unregister();
    }
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
  public synchronized Future<Void> writeMessage(Object message) {
    Promise<Void> promise = context.promise();
    writeMessage(message, promise);
    return promise.future();
  }

  @Override
  public NetSocketInternal writeMessage(Object message, Handler<AsyncResult<Void>> handler) {
    writeToChannel(message, handler == null ? null : context.promise(handler));
    return this;
  }

  @Override
  protected void reportsBytesWritten(Object msg) {
    if (msg instanceof ByteBuf) {
      reportBytesWritten(((ByteBuf)msg).readableBytes());
    }
  }

  @Override
  protected void reportBytesRead(Object msg) {
    if (msg instanceof ByteBuf) {
      reportBytesRead(((ByteBuf)msg).readableBytes());
    }
  }

  @Override
  public String applicationLayerProtocol() {
    return negotiatedApplicationLayerProtocol;
  }

  @Override
  public Future<Void> write(Buffer data) {
    return writeMessage(data.getByteBuf());
  }

  @Override
  public void write(String str, Handler<AsyncResult<Void>> handler) {
    write(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8), handler);
  }

  @Override
  public Future<Void> write(String str) {
    return writeMessage(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
  }

  @Override
  public Future<Void> write(String str, String enc) {
    return writeMessage(Unpooled.copiedBuffer(str, Charset.forName(enc)));
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
      messageHandler(new DataMessageHandler(dataHandler));
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
  public Future<Void> sendFile(String filename, long offset, long length) {
    Promise<Void> promise = context.promise();
    sendFile(filename, offset, length, promise);
    return promise.future();
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
  public Future<Void> upgradeToSsl() {
    Promise<Void> promise = context.promise();
    upgradeToSsl(promise);
    return promise.future();
  }

  @Override
  public Future<Void> upgradeToSsl(String serverName) {
    Promise<Void> promise = context.promise();
    upgradeToSsl(serverName, promise);
    return promise.future();
  }

  @Override
  public NetSocket upgradeToSsl(Handler<AsyncResult<Void>> handler) {
    return upgradeToSsl(null, handler);
  }

  @Override
  public NetSocket upgradeToSsl(String serverName, Handler<AsyncResult<Void>> handler) {
    ChannelOutboundHandler sslHandler = (ChannelOutboundHandler) chctx.pipeline().get("ssl");
    if (sslHandler == null) {
      ChannelPromise p = chctx.newPromise();
      chctx.pipeline().addFirst("handshaker", new SslHandshakeCompletionHandler(p));
      p.addListener(future -> {
        if (handler != null) {
          AsyncResult<Void> res;
          if (future.isSuccess()) {
            res = Future.succeededFuture();
          } else {
            res = Future.failedFuture(future.cause());
          }
          context.emit(res, handler);
        }
      });
      if (remoteAddress != null) {
        sslHandler = new SslHandler(helper.createEngine(vertx, remoteAddress, serverName, false));
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
    context.emit(null, v -> callDrainHandler());
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
    context.emit(InboundBuffer.END_SENTINEL, pending::write);
    super.handleClosed();
  }

  public void handleMessage(Object msg) {
    if (msg instanceof ByteBuf) {
      msg = VertxHandler.safeBuffer((ByteBuf) msg);
    }
    context.emit(msg, elt -> {
      if (!pending.write(elt)) {
        doPause();
      }
    });
  }

  private static class DataMessageHandler implements Handler<Object> {

    private final Handler<Buffer> dataHandler;

    DataMessageHandler(Handler<Buffer> dataHandler) {
      this.dataHandler = dataHandler;
    }

    @Override
    public void handle(Object msg) {
      if (msg instanceof ByteBuf) {
        ByteBuf byteBuf = (ByteBuf) msg;
        Buffer data = Buffer.buffer(byteBuf);
        dataHandler.handle(data);
      }
    }
  }

  private static class InvalidMessageHandler implements Handler<Object> {
    @Override
    public void handle(Object msg) {
      // ByteBuf are eagerly released when the message is processed
      if (msg instanceof ReferenceCounted && (!(msg instanceof ByteBuf))) {
        ReferenceCounted refCounter = (ReferenceCounted) msg;
        refCounter.release();
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

