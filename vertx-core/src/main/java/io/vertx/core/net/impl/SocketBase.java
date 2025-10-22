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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.net.SocketInternal;
import io.vertx.core.streams.impl.InboundBuffer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class SocketBase<S extends SocketBase<S>> extends VertxConnection implements SocketInternal {

  private final InboundMessageQueue<Object> pending;
  private Handler<Void> endHandler;
  private volatile Handler<Void> drainHandler;
  private Handler<Buffer> handler;
  private MessageHandler messageHandler;
  private Handler<Void> readCompletionHandler;
  private Handler<Object> eventHandler;
  private Handler<Void> shutdownHandler;

  public SocketBase(ContextInternal context, ChannelHandlerContext channel) {
    super(context, channel);
    this.messageHandler = new DataMessageHandler();
    this.pending = new InboundMessageQueue<>(context.eventLoop(), context.executor()) {
      @Override
      protected void handleResume() {
        SocketBase.this.doResume();
      }
      @Override
      protected void handlePause() {
        SocketBase.this.doPause();
      }
      @Override
      protected void handleMessage(Object msg) {
        if (msg == InboundBuffer.END_SENTINEL) {
          Handler<Void> handler = endHandler();
          if (handler != null) {
            context.dispatch(handler);
          }
        } else {
          MessageHandler handler = messageHandler();
          handler.handle(msg);
        }
      }
    };
  }

  @Override
  public Future<Void> writeMessage(Object message) {
    Promise<Void> promise = context.promise();
    writeToChannel(message, promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(Buffer data) {
    return writeMessage(((BufferInternal)data).getByteBuf());
  }

  @Override
  public Future<Void> write(String str) {
    return writeMessage(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
  }

  @Override
  public Future<Void> write(String str, String enc) {
    return writeMessage(Unpooled.copiedBuffer(str, Charset.forName(enc)));
  }

  private synchronized Handler<Buffer> handler() {
    return handler;
  }

  @Override
  public synchronized S handler(Handler<Buffer> dataHandler) {
    this.handler = dataHandler;
    return (S) this;
  }

  private synchronized MessageHandler messageHandler() {
    return messageHandler;
  }

  @Override
  public synchronized S messageHandler(Handler<Object> handler) {
    if (handler == null) {
      messageHandler = new DataMessageHandler();
    } else {
      messageHandler = new MessageHandler() {
        @Override
        public boolean accept(Object msg) {
          return true;
        }
        @Override
        public Object transform(Object msg) {
          return msg;
        }
        @Override
        public void handle(Object msg) {
          context.emit(msg, handler);
        }
      };
    }
    return (S) this;
  }

  private synchronized Handler<Void> readCompletionHandler() {
    return readCompletionHandler;
  }

  @Override
  public synchronized S readCompletionHandler(Handler<Void> handler) {
    readCompletionHandler = handler;
    return (S) this;
  }

  @Override
  public synchronized S eventHandler(Handler<Object> handler) {
    eventHandler = handler;
    return (S) this;
  }

  @Override
  public synchronized S pause() {
    pending.pause();
    return (S) this;
  }

  @Override
  public S fetch(long amount) {
    pending.fetch(amount);
    return (S) this;
  }

  @Override
  public synchronized S resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public S setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return (S) this;
  }

  @Override
  public boolean writeQueueFull() {
    return super.writeQueueFull();
  }

  @Override
  protected void handleWriteQueueDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      context.emit(null, handler);
    }
  }

  private synchronized Handler<Void> endHandler() {
    return endHandler;
  }

  @Override
  public synchronized S endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return (S) this;
  }

  @Override
  public synchronized S drainHandler(Handler<Void> drainHandler) {
    this.drainHandler = drainHandler;
    return (S) this;
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    PromiseInternal<Void> promise = context.promise();
    File file = vertx.fileResolver().resolve(filename);
    RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(file, "r");
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    long actualLength = Math.min(length, file.length() - offset);
    long actualOffset = Math.min(offset, file.length());
    ChannelFuture fut = sendFile(raf.getChannel(), actualOffset, actualLength);
    fut.addListener(promise);
    return promise.future().andThen(ar -> {
      try {
        raf.close();
      } catch (IOException ignore) {
      }
    });
  }

  public S exceptionHandler(Handler<Throwable> handler) {
    return (S) super.exceptionHandler(handler);
  }

  @Override
  public S closeHandler(Handler<Void> handler) {
    return (S) super.closeHandler(handler);
  }

  @Override
  public Future<Void> end() {
    return close();
  }

  public synchronized S shutdownHandler(@Nullable Handler<Void> handler) {
    shutdownHandler = handler;
    return (S) this;
  }

  @Override
  protected void handleShutdown(ChannelPromise promise) {
    Handler<Void> handler;
    synchronized (this) {
      handler = shutdownHandler;
    }
    if (handler != null) {
      context.emit(handler);
    } else {
      super.handleShutdown(promise);
    }
  }

  protected void handleEnd() {
    pending.write(InboundBuffer.END_SENTINEL);
  }

  @Override
  protected void handleMessage(Object msg) {
    MessageHandler handler = messageHandler();
    if (handler.accept(msg)) {
      pending.write(handler.transform(msg));
    } else {
      if (msg instanceof ReferenceCounted) {
        ReferenceCounted refCounter = (ReferenceCounted) msg;
        refCounter.release();
      }
    }
  }

  @Override
  protected void handleReadComplete() {
    Handler<Void> handler = readCompletionHandler();
    if (handler != null) {
      context.emit(handler);
    }
  }

  @Override
  protected void handleEvent(Object event) {
    Handler<Object> handler;
    synchronized (this) {
      handler = eventHandler;
    }
    if (handler != null) {
      context.emit(event, handler);
    } else {
      super.handleEvent(event);
    }
  }

  interface MessageHandler {

    boolean accept(Object msg);

    Object transform(Object msg);

    void handle(Object msg);
  }

  private class DataMessageHandler implements MessageHandler {

    @Override
    public boolean accept(Object msg) {
      return msg instanceof ByteBuf;
    }

    @Override
    public Object transform(Object msg) {
      return BufferInternal.safeBuffer((ByteBuf) msg);
    }

    @Override
    public void handle(Object msg) {
      Handler<Buffer> handler = handler();
      if (handler != null) {
        context.dispatch((Buffer)msg, handler);
      }
    }
  }
}

