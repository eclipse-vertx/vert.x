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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.traffic.AbstractTrafficShapingHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.streams.impl.OutboundWriteQueue;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;
import static io.vertx.core.streams.impl.OutboundWriteQueue.numberOfUnwritableSignals;

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

  private static final long METRICS_REPORTED_BYTES_LOW_MASK = 0xFFF; // 4K
  private static final long METRICS_REPORTED_BYTES_HIGH_MASK = ~METRICS_REPORTED_BYTES_LOW_MASK; // 4K

  /**
   * An exception used to signal a closed connection to an exception handler. Exception are
   * expensive to create, this instance can be used for this purpose. It does not capture a stack
   * trace to not be misleading.
   */
  public static final VertxException CLOSED_EXCEPTION = new VertxException("Connection was closed", true);
  public static final AttributeKey<SocketAddress> REMOTE_ADDRESS_OVERRIDE = AttributeKey.valueOf("RemoteAddressOverride");
  public static final AttributeKey<SocketAddress> LOCAL_ADDRESS_OVERRIDE = AttributeKey.valueOf("LocalAddressOverride");
  private static final Logger log = LoggerFactory.getLogger(ConnectionBase.class);
  private static final int MAX_REGION_SIZE = 1024 * 1024;

  public final VoidChannelPromise voidPromise;
  protected final VertxInternal vertx;
  protected final ChannelHandlerContext chctx;
  protected final ContextInternal context;
  private final OutboundWriteQueue<MessageWrite> writeQueue;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Object metric;
  private SocketAddress remoteAddress;
  private SocketAddress realRemoteAddress;
  private SocketAddress localAddress;
  private SocketAddress realLocalAddress;
  private ChannelPromise closePromise;
  private Future<Void> closeFuture;
  private long remainingBytesRead;
  private long remainingBytesWritten;
  private final AtomicInteger writeQueueFull = new AtomicInteger();

  // State accessed exclusively from the event loop thread
  private boolean read;
  private boolean needsFlush;
  private boolean closed;
  private boolean writable;
  private boolean overflow;
  private boolean draining;

  protected ConnectionBase(ContextInternal context, ChannelHandlerContext chctx) {
    this.vertx = context.owner();
    this.chctx = chctx;
    this.context = context;
    this.voidPromise = new VoidChannelPromise(chctx.channel(), false);
    this.closePromise = chctx.newPromise();
    this.writable = chctx.channel().isWritable();

    writeQueue = new OutboundWriteQueue<>(msg -> {
      if (writable) {
        msg.write();
        return true;
      } else {
        return false;
      }
    });
    PromiseInternal<Void> p = context.promise();
    closePromise.addListener(p);
    closeFuture = p.future();

    // Add close handler callback
    closeFuture.onComplete(this::checkCloseHandler);
  }

  private void startDraining() {
    draining = true;
  }

  private void stopDraining() {
    draining = false;
    if (!read && needsFlush) {
      needsFlush = false;
      chctx.flush();
    }
  }

  /**
   * @return a promise that will be completed when the connection becomes closed
   */
  public Future<Void> closeFuture() {
    return closeFuture;
  }

  /**
   * Fail the connection, the {@code error} will be sent to the pipeline and the connection will
   * stop processing any further message.
   *
   * @param error the {@code Throwable} to propagate
   */
  public void fail(Throwable error) {
    chctx.pipeline().fireExceptionCaught(error);
  }

  void close(ChannelPromise promise) {
    closePromise.addListener(l -> {
      if (l.isSuccess()) {
        promise.setSuccess();
      } else {
        promise.setFailure(l.cause());
      }
    });
    close();
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
    handleMessage(msg);
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
    if (flush) {
      chctx.writeAndFlush(msg, promise);
    } else {
      chctx.write(msg, promise);
    }
  }

  /**
   * This method is exclusively called on the event-loop thread
   *
   * @param promise the promise receiving the completion event
   */
  private void writeClose(PromiseInternal<Void> promise) {
    if (closed) {
      promise.complete();
      return;
    }
    closed = true;
    // Make sure everything is flushed out on close
    ChannelPromise channelPromise = chctx
      .newPromise()
      .addListener((ChannelFutureListener) f -> {
        chctx.close().addListener(promise);
      });
    writeToChannel(Unpooled.EMPTY_BUFFER, true, channelPromise);
  }

  protected final ChannelPromise wrap(FutureListener<Void> handler) {
    ChannelPromise promise = chctx.newPromise();
    promise.addListener(handler);
    return promise;
  }

  /**
   * Called when the connection write queue is drained
   */
  protected void writeQueueDrained() {
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
        ConnectionBase.this.write(msg, forceFlush, promise);
      }

      @Override
      public void cancel(Throwable cause) {
        promise.setFailure(cause);
      }
    });
  }

  public final boolean writeToChannel(MessageWrite msg) {
    EventExecutor eventLoop = chctx.executor();
    boolean inEventLoop = eventLoop.inEventLoop();
    int flags;
    if (inEventLoop) {
      flags = writeQueue.add(msg);
      overflow |= (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
      if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
        handleWriteQueueDrained(numberOfUnwritableSignals(flags));
      }
    } else {
      flags = writeQueue.submit(msg);
      if ((flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0) {
        eventLoop.execute(this::drainWriteQueue);
      }
    }
    if ((flags & OutboundWriteQueue.QUEUE_UNWRITABLE_MASK) != 0) {
      int val = writeQueueFull.incrementAndGet();
      return val <= 0;
    } else {
      return writeQueueFull.get() <= 0;
    }
  }

  private void drainWriteQueue() {
    startDraining();
    int flags = writeQueue.drain();
    overflow = (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
    if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
      handleWriteQueueDrained(numberOfUnwritableSignals(flags));
    }
    stopDraining();
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

  // This is a volatile read inside the Netty channel implementation
  public boolean isNotWritable() {
    return !chctx.channel().isWritable();
  }

  /**
   * @return the write queue writability status
   */
  public boolean writeQueueFull() {
    return writeQueueFull.get() > 0;
  }

  /**
   * Close the connection
   */
  public Future<Void> close() {
    PromiseInternal<Void> promise = context.promise();
    EventExecutor exec = chctx.executor();
    if (exec.inEventLoop()) {
      writeClose(promise);
    } else {
      exec.execute(() -> writeClose(promise));
    }
    return promise.future();
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

  protected void handleException(Throwable t) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      metrics.exceptionOccurred(metric, remoteAddress(), t);
    }
    context.emit(t, err -> {
      Handler<Throwable> handler;
      synchronized (ConnectionBase.this) {
        handler = exceptionHandler;
      }
      if (handler != null) {
        handler.handle(err);
      } else {
        if (log.isDebugEnabled()) {
          log.error(t.getMessage(), t);
        } else {
          log.error(t.getMessage());
        }
      }
    });
  }

  protected void handleClosed() {
    closed = true;
    List<MessageWrite> pending = writeQueue.clear();
    for (MessageWrite msg : pending) {
      msg.cancel(CLOSED_EXCEPTION);
    }
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      flushBytesRead();
      flushBytesWritten();
      if (metrics instanceof TCPMetrics) {
        ((TCPMetrics) metrics).disconnected(metric(), remoteAddress());
      }
    }
    closePromise.setSuccess();
  }

  private void checkCloseHandler(AsyncResult<Void> ar) {
    Handler<Void> handler;
    synchronized (ConnectionBase.this) {
      handler = closeHandler;
    }
    if (handler != null) {
      handler.handle(null);
    }
  }

  protected void handleEvent(Object evt) {
    // Will release the event if needed
    ReferenceCountUtil.release(evt);
  }

  /**
   * Called by the Netty handler when the connection becomes idle. The default implementation closes the
   * connection.
   * <p/>
   * Subclasses can override it to prevent the idle event to happen (e.g when the connection is pooled) or
   * perform extra work when the idle event happens.
   */
  protected void handleIdle(IdleStateEvent event) {
    log.debug("The connection will be closed due to timeout");
    chctx.close();
  }

  private void handleWriteQueueDrained(int times) {
    int val = writeQueueFull.addAndGet(-times);
    if ((val + times) > 0 && val <= 0) {
      writeQueueDrained();
    }
  }

  void handleChannelWritabilityChanged() {
    writable = chctx.channel().isWritable();
    if (writable && overflow) {
      startDraining();
      int flags = writeQueue.drain();
      overflow = (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
      if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
        handleWriteQueueDrained(numberOfUnwritableSignals(flags));
      }
      stopDraining();
    }
    handleInterestedOpsChanged(writable);
  }

  protected void handleInterestedOpsChanged(boolean writable) {
    handleInterestedOpsChanged();
  }

  protected void handleInterestedOpsChanged() {
  }

  protected boolean supportsFileRegion() {
    return vertx.transport().supportFileRegion() && !isSsl() &&!isTrafficShaped();
  }

  public final void reportBytesRead(Object msg) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      doReportBytesRead(msg, metrics);
    }
  }

  private void doReportBytesRead(Object msg, NetworkMetrics metrics) {
    long bytes = remainingBytesRead;
    long numberOfBytes = sizeof(msg);
    bytes += numberOfBytes;
    long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
    if (val > 0) {
      bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
      metrics.bytesRead(metric(), remoteAddress(), val);
    }
    remainingBytesRead = bytes;
  }

  protected long sizeof(Object msg) {
    if (msg instanceof ByteBuf) {
      return ((ByteBuf)msg).readableBytes();
    }
    return 0L;
  }

  public final void reportBytesRead(long numberOfBytes) {
    if (numberOfBytes < 0L) {
      throw new IllegalArgumentException();
    }
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long bytes = remainingBytesRead;
      bytes += numberOfBytes;
      long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
      if (val > 0) {
        bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
        metrics.bytesRead(metric(), remoteAddress(), val);
      }
      remainingBytesRead = bytes;
    }
  }

  public final void reportsBytesWritten(Object msg) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long numberOfBytes = sizeof(msg);
      long bytes = remainingBytesWritten;
      bytes += numberOfBytes;
      long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
      if (val > 0) {
        bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
        metrics.bytesWritten(metric, remoteAddress(), val);
      }
      remainingBytesWritten = bytes;
    }
  }

  public final void reportBytesWritten(long numberOfBytes) {
    if (numberOfBytes < 0L) {
      throw new IllegalArgumentException();
    }
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long bytes = remainingBytesWritten;
      bytes += numberOfBytes;
      long val = bytes & METRICS_REPORTED_BYTES_HIGH_MASK;
      if (val > 0) {
        bytes &= METRICS_REPORTED_BYTES_LOW_MASK;
        metrics.bytesWritten(metric, remoteAddress(), val);
      }
      remainingBytesWritten = bytes;
    }
  }

  public void flushBytesRead() {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long val = remainingBytesRead;
      if (val > 0L) {
        remainingBytesRead = 0L;
        metrics.bytesRead(metric(), remoteAddress(), val);
      }
    }
  }

  public void flushBytesWritten() {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      long val = remainingBytesWritten;
      if (val > 0L) {
        remainingBytesWritten = 0L;
        metrics.bytesWritten(metric(), remoteAddress(), val);
      }
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

  public boolean isSsl() {
    return chctx.pipeline().get(SslHandler.class) != null;
  }

  public boolean isTrafficShaped() {
    return chctx.pipeline().get(AbstractTrafficShapingHandler.class) != null;
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

  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    SSLSession session = sslSession();
    if (session != null) {
      return Arrays.asList(session.getPeerCertificates());
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

  private SocketAddress channelRemoteAddress() {
    java.net.SocketAddress addr = chctx.channel().remoteAddress();
    return addr != null ? vertx.transport().convert(addr) : null;
  }

  private SocketAddress socketAdressOverride(AttributeKey<SocketAddress> key) {
    Channel ch = chctx.channel();
    return ch.hasAttr(key) ? ch.attr(key).getAndSet(null) : null;
  }

  public SocketAddress remoteAddress() {
    SocketAddress address = remoteAddress;
    if (address == null) {
      address = socketAdressOverride(REMOTE_ADDRESS_OVERRIDE);
      if (address == null) {
        address = channelRemoteAddress();
        if (address != null && address.isDomainSocket() && address.path().isEmpty()) {
          address = channelLocalAddress();
        }
      }
      if (address != null) {
        remoteAddress = address;
      }
    }
    return address;
  }

  public SocketAddress remoteAddress(boolean real) {
    if (real) {
      SocketAddress address = realRemoteAddress;
      if (address == null) {
        address = channelRemoteAddress();
      }
      if (address != null) {
        realRemoteAddress = address;
      }
      return address;
    } else {
      return remoteAddress();
    }
  }

  private SocketAddress channelLocalAddress() {
    java.net.SocketAddress addr = chctx.channel().localAddress();
    return addr != null ? vertx.transport().convert(addr) : null;
  }

  public SocketAddress localAddress() {
    SocketAddress address = localAddress;
    if (address == null) {
      address = socketAdressOverride(LOCAL_ADDRESS_OVERRIDE);
      if (address == null) {
        address = channelLocalAddress();
        if (address != null && address.isDomainSocket() && address.path().isEmpty()) {
          address = channelRemoteAddress();
        }
      }
      if (address != null) {
        localAddress = address;
      }
    }
    return address;
  }

  public SocketAddress localAddress(boolean real) {
    if (real) {
      SocketAddress address = realLocalAddress;
      if (address == null) {
        address = channelLocalAddress();
      }
      if (address != null) {
        realLocalAddress = address;
      }
      return address;
    } else {
      return localAddress();
    }
  }

  protected void handleMessage(Object msg) {
  }
}
