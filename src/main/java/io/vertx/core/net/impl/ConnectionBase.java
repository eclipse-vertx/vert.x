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

import io.netty.channel.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.vertx.core.*;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(ConnectionBase.class);

  protected final VertxInternal vertx;
  protected final ChannelHandlerContext chctx;
  protected final ContextImpl context;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private boolean read;
  private boolean needsFlush;
  private int writeInProgress;
  private Object metric;

  protected ConnectionBase(VertxInternal vertx, ChannelHandlerContext chctx, ContextImpl context) {
    this.vertx = vertx;
    this.chctx = chctx;
    this.context = context;
  }

  /**
   * Encode to message before writing to the channel
   *
   * @param obj the object to encode
   * @return the encoded message
   */
  protected Object encode(Object obj) {
    return obj;
  }

  public synchronized final void startRead() {
    checkContext();
    read = true;
  }

  protected synchronized final void endReadAndFlush() {
    if (read) {
      read = false;
      if (needsFlush && writeInProgress == 0) {
        needsFlush = false;
        chctx.flush();
      }
    }
  }

  private void write(Object msg, ChannelPromise promise) {
    msg = encode(msg);
    if (read || writeInProgress > 0) {
      needsFlush = true;
      chctx.write(msg, promise);
    } else {
      needsFlush = false;
      chctx.writeAndFlush(msg, promise);
    }
  }

  public synchronized void writeToChannel(Object msg, ChannelPromise promise) {
    // Make sure we serialize all the messages as this method can be called from various threads:
    // two "sequential" calls to writeToChannel (we can say that as it is synchronized) should preserve
    // the message order independently of the thread. To achieve this we need to reschedule messages
    // not on the event loop or if there are pending async message for the channel.
    if (chctx.executor().inEventLoop() && writeInProgress == 0) {
      write(msg, promise);
    } else {
      queueForWrite(msg, promise);
    }
  }

  private void queueForWrite(Object msg, ChannelPromise promise) {
    writeInProgress++;
    context.runOnContext(v -> {
      synchronized (ConnectionBase.this) {
        writeInProgress--;
        write(msg, promise);
      }
    });
  }

  public void writeToChannel(Object obj) {
    writeToChannel(obj, chctx.voidPromise());
  }

  // This is a volatile read inside the Netty channel implementation
  public boolean isNotWritable() {
    return !chctx.channel().isWritable();
  }

  /**
   * Close the connection
   */
  public void close() {
    // make sure everything is flushed out on close
    endReadAndFlush();
    chctx.channel().close();
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
    int high = config.getWriteBufferHighWaterMark();
    int newLow = size / 2;
    int newHigh = size;
    if (newLow >= high) {
      config.setWriteBufferHighWaterMark(newHigh);
      config.setWriteBufferLowWaterMark(newLow);
    } else {
      config.setWriteBufferLowWaterMark(newLow);
      config.setWriteBufferHighWaterMark(newHigh);
    }
  }

  protected void checkContext() {
    // Sanity check
    if (context != vertx.getContext()) {
      throw new IllegalStateException("Wrong context!");
    }
  }

  /**
   * @return the Netty channel - for internal usage only
   */
  public Channel channel() {
    return chctx.channel();
  }

  public ContextImpl getContext() {
    return context;
  }

  public synchronized void metric(Object metric) {
    this.metric = metric;
  }

  public synchronized Object metric() {
    return metric;
  }

  public abstract NetworkMetrics metrics();

  protected synchronized void handleException(Throwable t) {
    NetworkMetrics metrics = metrics();
    if (metrics != null) {
      metrics.exceptionOccurred(metric, remoteAddress(), t);
    }
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    } else {
      log.error(t);
    }
  }

  protected synchronized void handleClosed() {
    NetworkMetrics metrics = metrics();
    if (metrics != null && metrics instanceof TCPMetrics) {
      ((TCPMetrics) metrics).disconnected(metric(), remoteAddress());
    }
    if (closeHandler != null) {
      vertx.runOnContext(closeHandler);
    }
  }

  protected abstract void handleInterestedOpsChanged();

  protected void addFuture(final Handler<AsyncResult<Void>> completionHandler, final ChannelFuture future) {
    if (future != null) {
      future.addListener(channelFuture -> context.executeFromIO(() -> {
        if (completionHandler != null) {
          if (channelFuture.isSuccess()) {
            completionHandler.handle(Future.succeededFuture());
          } else {
            completionHandler.handle(Future.failedFuture(channelFuture.cause()));
          }
        } else if (!channelFuture.isSuccess()) {
          handleException(channelFuture.cause());
        }
      }));
    }
  }

  protected boolean supportsFileRegion() {
    return !isSSL();
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

  public boolean isSSL() {
    return chctx.pipeline().get(SslHandler.class) != null;
  }

  protected ChannelFuture sendFile(RandomAccessFile raf, long offset, long length) throws IOException {
    // Write the content.
    ChannelPromise writeFuture = chctx.newPromise();
    if (!supportsFileRegion()) {
      // Cannot use zero-copy
      writeToChannel(new ChunkedFile(raf, offset, length, 8192), writeFuture);
    } else {
      // No encryption - use zero-copy.
      FileRegion region = new DefaultFileRegion(raf.getChannel(), offset, length);
      writeToChannel(region, writeFuture);
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
    if (isSSL()) {
      ChannelHandlerContext sslHandlerContext = chctx.pipeline().context("ssl");
      assert sslHandlerContext != null;
      SslHandler sslHandler = (SslHandler) sslHandlerContext.handler();
      return sslHandler.engine().getSession();
    } else {
      return null;
    }
  }

  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    if (isSSL()) {
      ChannelHandlerContext sslHandlerContext = chctx.pipeline().context("ssl");
      assert sslHandlerContext != null;
      SslHandler sslHandler = (SslHandler) sslHandlerContext.handler();
      return sslHandler.engine().getSession().getPeerCertificateChain();
    } else {
      return null;
    }
  }

  public String indicatedServerName() {
    if (chctx.channel().hasAttr(VertxSniHandler.SERVER_NAME_ATTR)) {
      return chctx.channel().attr(VertxSniHandler.SERVER_NAME_ATTR).get();
    } else {
      return null;
    }
  }

  public ChannelPromise channelFuture() {
    return chctx.newPromise();
  }

  public String remoteName() {
    InetSocketAddress addr = (InetSocketAddress) chctx.channel().remoteAddress();
    if (addr == null) return null;
    // Use hostString that does not trigger a DNS resolution
    return addr.getHostString();
  }

  public SocketAddress remoteAddress() {
    InetSocketAddress addr = (InetSocketAddress) chctx.channel().remoteAddress();
    if (addr == null) return null;
    return new SocketAddressImpl(addr);
  }

  public SocketAddress localAddress() {
    InetSocketAddress addr = (InetSocketAddress) chctx.channel().localAddress();
    if (addr == null) return null;
    return new SocketAddressImpl(addr);
  }
}
