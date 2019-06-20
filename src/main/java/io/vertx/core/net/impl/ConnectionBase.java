/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.vertx.core.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;

/**
 * Abstract base class for TCP connections.
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ConnectionBase {

  /**
   * An exception used to signal a closed connection to an exception handler. Exception are
   * expensive to create, this instance can be used for this purpose. It does not capture a stack
   * trace to not be misleading.
   */
  public static final VertxException CLOSED_EXCEPTION = new VertxException("Connection was closed", true);
  private static final Logger log = LoggerFactory.getLogger(ConnectionBase.class);
  private static final int MAX_REGION_SIZE = 1024 * 1024;

  public final VoidChannelPromise voidPromise;
  protected final VertxInternal vertx;
  protected final ChannelHandlerContext chctx;
  protected final ContextInternal context;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private int writeInProgress;
  private Object metric;

  // State accessed exclusively from the event loop thread
  private boolean read;
  private boolean needsFlush;

  protected ConnectionBase(VertxInternal vertx, ChannelHandlerContext chctx, ContextInternal context) {
    this.vertx = vertx;
    this.chctx = chctx;
    this.context = context;
    this.voidPromise = new VoidChannelPromise(chctx.channel(), false);
  }

  /**
   * Fail the connection, the {@code error} will be sent to the pipeline and the connection will
   * stop processing any further message.
   *
   * @param error the {@code Throwable} to propagate
   */
  public void fail(Throwable error) {
    handler().fail(error);
  }

  public VertxHandler handler() {
    return (VertxHandler) chctx.handler();
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
   * This method is exclusively called by {@code VertxHandler} to signal read on the event-loop thread.
   */
  final void setRead() {
    read = true;
  }

  /**
   * This method is exclusively called on the event-loop thread
   *
   * @param msg the messsage to write
   * @param flush {@code true} to perform a write and flush operation
   * @param promise the promise receiving the completion event
   */
  private void write(Object msg, boolean flush, ChannelPromise promise) {
    needsFlush = !flush;
    if (flush) {
      chctx.writeAndFlush(msg, promise);
    } else {
      chctx.write(msg, promise);
    }
  }

  /**
   * Provide a promise that will call the {@code handler} upon completion.
   * When the {@code handler} is {@code null} {@link #voidPromise} is returned.
   *
   * @param handler the handler
   * @return a promise
   */
  public final ChannelPromise toPromise(Handler<AsyncResult<Void>> handler) {
    return handler == null ? voidPromise : wrap(handler);
  }

  private ChannelPromise wrap(Handler<AsyncResult<Void>> handler) {
    ChannelPromise promise = chctx.newPromise();
    promise.addListener((fut) -> {
        if (fut.isSuccess()) {
          handler.handle(Future.succeededFuture());
        } else {
          handler.handle(Future.failedFuture(fut.cause()));
        }
      }
    );
    return promise;
  }

  public void writeToChannel(Object msg, ChannelPromise promise) {
    synchronized (this) {
      if (!chctx.executor().inEventLoop() || writeInProgress > 0) {
        // Make sure we serialize all the messages as this method can be called from various threads:
        // two "sequential" calls to writeToChannel (we can say that as it is synchronized) should preserve
        // the message order independently of the thread. To achieve this we need to reschedule messages
        // not on the event loop or if there are pending async message for the channel.
        queueForWrite(msg, promise);
        return;
      }
    }
    // On the event loop thread
    write(msg, !read, promise);
  }

  private void queueForWrite(Object msg, ChannelPromise promise) {
    writeInProgress++;
    chctx.executor().execute(() -> {
      boolean flush;
      synchronized (this) {
        flush = --writeInProgress == 0 && !read;
      }
      write(msg, flush, promise);
    });
  }

  public void writeToChannel(Object obj) {
    writeToChannel(obj, voidPromise);
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
    if (chctx.executor().inEventLoop()) {
      if (needsFlush) {
        needsFlush = false;
        chctx.writeAndFlush(Unpooled.EMPTY_BUFFER, promise);
      } else {
        promise.setSuccess();
      }
    } else {
      chctx.executor().execute(() -> flush(promise));
    }
  }

  // This is a volatile read inside the Netty channel implementation
  public boolean isNotWritable() {
    return !chctx.channel().isWritable();
  }

  /**
   * Close the connection
   */
  public Future<Void> close() {
    Promise<Void> promise = Promise.promise();
    close(promise);
    return promise.future();
  }

  /**
   * Close the connection and notifies the {@code handler}
   */
  public void close(Handler<AsyncResult<Void>> handler) {
    // make sure everything is flushed out on close
    ChannelPromise promise = chctx
      .newPromise()
      .addListener((ChannelFutureListener) f -> {
      ChannelFuture closeFut = chctx.channel().close();
      if (handler != null) {
        closeFut.addListener(new ChannelFutureListenerAdapter<>(context, null, handler));
      }
    });
    flush(promise);
  }

  public synchronized ConnectionBase closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  public synchronized ConnectionBase exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  protected synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  public void doPause() {
    chctx.channel().config().setAutoRead(false);
  }

  public void doResume() {
    chctx.channel().config().setAutoRead(true);
  }

  public void doSetWriteQueueMaxSize(int size) {
    ChannelConfig config = chctx.channel().config();
    config.setWriteBufferWaterMark(new WriteBufferWaterMark(size / 2, size));
  }

  /**
   * @return the Netty channel - for internal usage only
   */
  public final Channel channel() {
    return chctx.channel();
  }

  public final ChannelHandlerContext channelHandlerContext() {
    return chctx;
  }

  public final ContextInternal getContext() {
    return context;
  }

  public final synchronized void metric(Object metric) {
    this.metric = metric;
  }

  public final synchronized Object metric() {
    return metric;
  }

  public abstract NetworkMetrics metrics();

  protected synchronized void handleException(Throwable t) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      metrics.exceptionOccurred(metric, remoteAddress(), t);
    }
    if (exceptionHandler != null) {
      context.dispatch(t, exceptionHandler);
    } else {
      if (log.isDebugEnabled()) {
        log.error(t.getMessage(), t);
      } else {
        log.error(t.getMessage());
      }
    }
  }

  protected void handleClosed() {
    Handler<Void> handler;
    synchronized (this) {
      NetworkMetrics metrics = metrics();
      if (metrics instanceof TCPMetrics) {
        ((TCPMetrics) metrics).disconnected(metric(), remoteAddress());
      }
      handler = closeHandler;
    }
    if (handler != null) {
      context.dispatch(handler);
    }
  }

  /**
   * Called by the Netty handler when the connection becomes idle. The default implementation closes the
   * connection.
   * <p/>
   * Subclasses can override it to prevent the idle event to happen (e.g when the connection is pooled) or
   * perform extra work when the idle event happens.
   */
  protected void handleIdle() {
    chctx.close();
  }

  protected abstract void handleInterestedOpsChanged();

  protected boolean supportsFileRegion() {
    return !isSsl();
  }

  public void reportBytesRead(long numberOfBytes) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      metrics.bytesRead(metric(), remoteAddress(), numberOfBytes);
    }
  }

  public void reportBytesWritten(long numberOfBytes) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      metrics.bytesWritten(metric(), remoteAddress(), numberOfBytes);
    }
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

  public final ChannelFuture sendFile(RandomAccessFile raf, long offset, long length) throws IOException {
    // Write the content.
    ChannelPromise writeFuture = chctx.newPromise();
    if (!supportsFileRegion()) {
      // Cannot use zero-copy
      writeToChannel(new ChunkedFile(raf, offset, length, 8192), writeFuture);
    } else {
      // No encryption - use zero-copy.
      sendFileRegion(raf, offset, length, writeFuture);
    }
    if (writeFuture != null) {
      writeFuture.addListener(fut -> raf.close());
    } else {
      raf.close();
    }
    return writeFuture;
  }

  public boolean isSsl() {
    return chctx.pipeline().get(SslHandler.class) != null;
  }

  public SSLSession sslSession() {
    ChannelHandlerContext sslHandlerContext = chctx.pipeline().context(SslHandler.class);
    if (sslHandlerContext != null) {
      SslHandler sslHandler = (SslHandler) sslHandlerContext.handler();
      return sslHandler.engine().getSession();
    } else {
      return null;
    }
  }

  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    SSLSession session = sslSession();
    if (session != null) {
      return session.getPeerCertificateChain();
    } else {
      return null;
    }
  }

  public String indicatedServerName() {
    if (chctx.channel().hasAttr(SslHandshakeCompletionHandler.SERVER_NAME_ATTR)) {
      return chctx.channel().attr(SslHandshakeCompletionHandler.SERVER_NAME_ATTR).get();
    } else {
      return null;
    }
  }

  public ChannelPromise channelFuture() {
    return chctx.newPromise();
  }

  public String remoteName() {
    java.net.SocketAddress addr = chctx.channel().remoteAddress();
    if (addr instanceof InetSocketAddress) {
      // Use hostString that does not trigger a DNS resolution
      return ((InetSocketAddress)addr).getHostString();
    }
    return null;
  }

  public SocketAddress remoteAddress() {
    java.net.SocketAddress addr = chctx.channel().remoteAddress();
    if (addr != null) {
      return vertx.transport().convert(addr);
    }
    return null;
  }

  public SocketAddress localAddress() {
    java.net.SocketAddress addr = chctx.channel().localAddress();
    if (addr != null) {
      return vertx.transport().convert(addr);
    }
    return null;
  }

  public void handleMessage(Object msg) {
  }
}
