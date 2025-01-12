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

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * Extend {@link ConnectionBase}.
 *
 * <ul>
 *   <li>Inbound/outbound message flow with back-pressure</li>
 *   <li>Channel graceful shutdown</li>
 * </ul>
 *
 * This handler should to be used with {@link VertxHandler}
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxConnection extends ConnectionBase {

  private static final Logger log = LoggerFactory.getLogger(VertxConnection.class);

  private static final int MAX_REGION_SIZE = 1024 * 1024;

  public final VoidChannelPromise voidPromise;
  private final OutboundMessageQueue<MessageWrite> messageQueue;
  private Handler<Void> shutdownHandler;

  // State accessed exclusively from the event loop thread
  private boolean read;
  private boolean needsFlush;
  private boolean draining;
  private boolean channelWritable;
  private boolean paused;
  private Deque<Object> pending;
  private boolean autoRead;
  private ScheduledFuture<?> shutdownTimeout;

  public VertxConnection(ContextInternal context, ChannelHandlerContext chctx) {
    super(context, chctx);
    this.channelWritable = chctx.channel().isWritable();
    this.messageQueue = new InternalMessageQueue(chctx.channel().eventLoop());
    this.voidPromise = new VoidChannelPromise(chctx.channel(), false);
  }

  public synchronized ConnectionBase shutdownHandler(@Nullable Handler<Void> handler) {
    shutdownHandler = handler;
    return this;
  }

  public final Future<Void> shutdown(long timeout, TimeUnit unit) {
    return shutdown(null, timeout, unit);
  }

  public final Future<Void> shutdown(Object reason, long timeout, TimeUnit unit) {
    Promise<Void> promise = vertx.promise();
    EventExecutor eventLoop = chctx.executor();
    if (eventLoop.inEventLoop()) {
      shutdown(reason, timeout, unit, promise);
    } else {
      eventLoop.execute(() -> shutdown(reason, timeout, unit, promise));
    }
    return promise.future();
  }

  private void shutdown(Object reason, long timeout, TimeUnit unit, Promise<Void> promise) {
    close(reason, timeout, unit).onComplete(promise); // Perhaps optimized this with internal stuff
  }

  /**
   * Called by a Netty handler to relay user {@code event}, the default implementation handles
   * {@link ShutdownEvent} and {@link ReferenceCounted}.
   * <ul>
   *   <li>{@code ShutdownEvent} trigger a channel shutdown</li>
   *   <li>{@code ReferencedCounter} is released</li>
   * </ul>
   * Subclasses can override it to handle user events.
   * <p/>
   * This method is exclusively called on the event-loop thread and relays a channel user event.
   * @param event the event.
   */
  protected void handleEvent(Object event) {
    if (event instanceof ShutdownEvent) {
      ShutdownEvent shutdown = (ShutdownEvent) event;
      shutdown(shutdown.timeout(), shutdown.timeUnit());
    } else {
      // Will release the event if needed
      ReferenceCountUtil.release(event);
    }
  }

  /**
   * Called by the Netty handler when the connection becomes idle. The default implementation closes the
   * connection.
   * <p/>
   * Subclasses can override it to prevent the idle event to happen (e.g. when the connection is pooled) or
   * perform extra work when the idle event happens.
   * <p/>
   * This method is exclusively called on the event-loop thread and relays a channel user event.
   */
  protected void handleIdle(IdleStateEvent event) {
    log.debug("The connection will be closed due to timeout");
    chctx.close();
  }

  protected boolean supportsFileRegion() {
    return vertx.transport().supportFileRegion() && !isSsl() &&!isTrafficShaped();
  }

  protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
    // Assert from event-loop
    ScheduledFuture<?> t = shutdownTimeout;
    if (t != null) {
      shutdownTimeout = null;
      t.cancel(false);
      super.handleClose(reason, 0L, TimeUnit.SECONDS, promise);
    }
  }

  @Override
  final void handleClose(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
    if (timeout == 0L) {
      super.handleClose(reason, timeout, unit, promise);
    } else {
      EventExecutor el = chctx.executor();
      shutdownTimeout = el.schedule(() -> {
        shutdownTimeout = null;
        super.handleClose(reason, 0L, TimeUnit.SECONDS, promise);
      }, timeout, unit);
      Handler<Void> handler;
      synchronized (this) {
        handler = shutdownHandler;
      }
      if (handler != null) {
        context.emit(handler);
      }
      handleShutdown(reason, timeout, unit, promise);
    }
  }

  /**
   * Called by the Netty handler when the connection becomes closed. The default implementation flushes and closes the
   * connection.
   * <p/>
   * Subclasses can override it to intercept the channel close and implement the close operation, this method should
   * always be called to proceed with the close control flow.
   * <p/>
   * This method is exclusively called on the event-loop thread and relays a channel user event.
   */
  @Override
  protected void handleClose(Object reason, ChannelPromise promise) {
    writeClose(promise);
  }

  protected void handleClosed() {
    ScheduledFuture<?> timeout = shutdownTimeout;
    if (timeout != null) {
      shutdownTimeout = null;
      timeout.cancel(false);
    }
    messageQueue.close();
    super.handleClosed();
  }

  /**
   * Called when the connection write queue is drained
   */
  protected void handleWriteQueueDrained() {
  }

  protected void handleMessage(Object msg) {
  }

  void channelWritabilityChanged() {
    channelWritable = chctx.channel().isWritable();
    if (channelWritable) {
      messageQueue.drain();
    }
  }

  /**
   * This method is exclusively called by {@code VertxHandler} to signal read completion on the event-loop thread.
   */
  final void endReadAndFlush() {
    if (read) {
      read = false;
      if (needsFlush) {
        needsFlush = false;
        chctx.flush();
      }
    }
  }

  /**
   * This method is exclusively called by {@code VertxHandler} to read a message on the event-loop thread.
   */
  final void read(Object msg) {
    read = true;
    if (METRICS_ENABLED) {
      reportBytesRead(msg);
    }
    if (paused) {
      addPending(msg);
      return;
    }
    handleMessage(msg);
  }

  private void addPending(Object msg) {
    if (pending == null) {
      pending = new ArrayDeque<>();
    }
    pending.add(msg);
    if (pending.size() >= 8) {
      autoRead = false;
      chctx.channel().config().setAutoRead(false);
    }
  }

  /**
   * Like {@link #write(Object, boolean, ChannelPromise)}.
   */
  public void write(Object msg, boolean forceFlush, FutureListener<Void> promise) {
    write(msg, forceFlush, wrap(promise));
  }

  /**
   * This method must be exclusively called on the event-loop thread.
   *
   * <p>This method directly writes to the channel pipeline and bypasses the outbound queue.</p>
   *
   * @param msg the message to write
   * @param forceFlush flush when {@code true} or there is no read in progress
   * @param promise the promise receiving the completion event
   */
  public void write(Object msg, boolean forceFlush, ChannelPromise promise) {
    assert chctx.executor().inEventLoop();
    if (METRICS_ENABLED) {
      reportsBytesWritten(msg);
    }
    boolean flush = (!read && !draining) || forceFlush;
    needsFlush = !flush;

    VertxHandler vertxHandler = chctx.pipeline().get(VertxHandler.class);
    if (vertxHandler != null) {
      vertxHandler.write(chctx, msg, promise, flush);
    } else {
      if (flush) {
        chctx.writeAndFlush(msg, promise);
      } else {
        chctx.write(msg, promise);
      }
    }
  }

  /**
   * This method is exclusively called on the event-loop thread
   *
   * @param promise the promise receiving the completion event
   */
  private void writeClose(ChannelPromise promise) {
    // Make sure everything is flushed out on close
    ChannelPromise channelPromise = chctx
      .newPromise()
      .addListener((ChannelFutureListener) f -> {
        chctx.close(promise);
      });
    writeToChannel(Unpooled.EMPTY_BUFFER, true, channelPromise);
  }

  public final boolean writeToChannel(Object obj) {
    return writeToChannel(obj, voidPromise);
  }

  public final boolean writeToChannel(Object msg, FutureListener<Void> listener) {
    return writeToChannel(msg, listener == null ? voidPromise : wrap(listener));
  }

  public final boolean writeToChannel(Object msg, ChannelPromise promise) {
    return writeToChannel(msg, false, promise);
  }

  public final boolean writeToChannel(Object msg, boolean forceFlush, ChannelPromise promise) {
    return writeToChannel(new MessageWrite() {
      @Override
      public void write() {
        VertxConnection.this.write(msg, forceFlush, promise);
      }

      @Override
      public void cancel(Throwable cause) {
        promise.setFailure(cause);
      }
    });
  }

  public final boolean writeToChannel(MessageWrite msg) {
    return messageQueue.write(msg);
  }

  /**
   * Asynchronous flush.
   */
  public final void flush() {
    flush(voidPromise);
  }

  /**
   * Asynchronous flush.
   *
   * @param promise the promise resolved when flush occurred
   */
  public final void flush(ChannelPromise promise) {
    writeToChannel(Unpooled.EMPTY_BUFFER, true, promise);
  }

  /**
   * Asynchronous flush.
   *
   * @param listener the listener notified when flush occurred
   */
  public final void flush(FutureListener<Void> listener) {
    writeToChannel(Unpooled.EMPTY_BUFFER, true, listener == null ? voidPromise : wrap(listener));
  }

  /**
   * @return the write queue writability status
   */
  public boolean writeQueueFull() {
    return !messageQueue.isWritable();
  }

  /**
   * Send a file as a file region for zero copy transfer to the socket.
   *
   * The implementation splits the file into multiple regions to avoid stalling the pipeline
   * and producing idle timeouts for very large files.
   *
   * @param file the file to send
   * @param offset the file offset
   * @param length the file length
   * @param writeFuture the write future to be completed when the transfer is done or failed
   */
  private void sendFileRegion(RandomAccessFile file, long offset, long length, ChannelPromise writeFuture) {
    if (length < MAX_REGION_SIZE) {
      writeToChannel(new DefaultFileRegion(file.getChannel(), offset, length), writeFuture);
    } else {
      ChannelPromise promise = chctx.newPromise();
      FileRegion region = new DefaultFileRegion(file.getChannel(), offset, MAX_REGION_SIZE);
      // Retain explicitly this file region so the underlying channel is not closed by the NIO channel when it
      // as been sent as we need it again
      region.retain();
      writeToChannel(region, promise);
      promise.addListener(future -> {
        if (future.isSuccess()) {
          sendFileRegion(file, offset + MAX_REGION_SIZE, length - MAX_REGION_SIZE, writeFuture);
        } else {
          log.error(future.cause().getMessage(), future.cause());
          writeFuture.setFailure(future.cause());
        }
      });
    }
  }

  public ChannelFuture sendFile(RandomAccessFile raf, long offset, long length) {
    // Write the content.
    ChannelPromise writeFuture = chctx.newPromise();
    if (!supportsFileRegion()) {
      // Cannot use zero-copy
      try {
        writeToChannel(new ChunkedNioFile(raf.getChannel(), offset, length, 8192), writeFuture);
      } catch (IOException e) {
        return chctx.newFailedFuture(e);
      }
    } else {
      // No encryption - use zero-copy.
      sendFileRegion(raf, offset, length, writeFuture);
    }
    writeFuture.addListener(fut -> raf.close());
    return writeFuture;
  }

  /**
   * Version of {@link OutboundMessageQueue} accessing internal connection base state.
   */
  private class InternalMessageQueue extends OutboundMessageQueue<MessageWrite> implements Predicate<MessageWrite> {

    public InternalMessageQueue(EventLoop eventLoop) {
      super(eventLoop);
    }

    @Override
    public boolean test(MessageWrite msg) {
      if (channelWritable) {
        msg.write();
        return true;
      } else {
        return false;
      }
    }

    @Override
    protected void disposeMessage(MessageWrite write) {
      write.cancel(CLOSED_EXCEPTION);
    }

    @Override
    protected void startDraining() {
      draining = true;
    }

    @Override
    protected void stopDraining() {
      draining = false;
      if (!read && needsFlush) {
        needsFlush = false;
        chctx.flush();
      }
    }

    @Override
    protected void writeQueueDrained() {
      VertxConnection.this.handleWriteQueueDrained();
    }
  }

  public final void doPause() {
    assert chctx.executor().inEventLoop();
    paused = true;
  }

  public final void doResume() {
    assert chctx.executor().inEventLoop();
    if (!paused) {
      return;
    }
    paused = false;
    if (pending != null && !pending.isEmpty()) {
      boolean end = !read;
      read = true;
      try {
        Object msg;
        while (!paused && (msg = pending.poll()) != null) {
          handleMessage(msg);
        }
      } finally {
        if (end) {
          endReadAndFlush();
        }
        if (pending.isEmpty() && !autoRead) {
          autoRead = true;
          chctx.channel().config().setAutoRead(true);
        }
      }
    }
  }

  public void doSetWriteQueueMaxSize(int size) {
    ChannelConfig config = chctx.channel().config();
    config.setWriteBufferWaterMark(new WriteBufferWaterMark(size / 2, size));
  }
}
