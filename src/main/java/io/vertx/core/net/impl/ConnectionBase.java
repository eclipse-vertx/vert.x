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
  protected final Channel channel;
  protected final ContextImpl context;
  protected final NetworkMetrics metrics;
  protected Handler<Throwable> exceptionHandler;
  protected Handler<Void> closeHandler;
  private boolean read;
  private boolean needsFlush;
  private Thread ctxThread;
  private boolean needsAsyncFlush;

  protected ConnectionBase(VertxInternal vertx, Channel channel, ContextImpl context, NetworkMetrics metrics) {
    this.vertx = vertx;
    this.channel = channel;
    this.context = context;
    this.metrics = metrics;
  }

  protected synchronized final void startRead() {
    checkContext();
    read = true;
    if (ctxThread == null) {
      ctxThread = Thread.currentThread();
    }
  }

  protected synchronized final void endReadAndFlush() {
    read = false;
    if (needsFlush) {
      needsFlush = false;
      if (needsAsyncFlush) {
        // If the connection has been written to from outside the event loop thread e.g. from a worker thread
        // Then Netty might end up executing the flush *before* the write as Netty checks for event loop and if not
        // it executes using the executor.
        // To avoid this ordering issue we must runOnContext in this situation
        context.runOnContext(v -> channel.flush());
      } else {
        // flush now
        channel.flush();
      }
    }
  }

  public synchronized ChannelFuture queueForWrite(final Object obj) {
    needsFlush = true;
    needsAsyncFlush = Thread.currentThread() != ctxThread;
    return channel.write(obj);
  }

  public synchronized ChannelFuture writeToChannel(Object obj) {
    if (read) {
      return queueForWrite(obj);
    }
    if (channel.isOpen()) {
      return channel.writeAndFlush(obj);
    } else {
      return null;
    }
  }

  // This is a volatile read inside the Netty channel implementation
  public boolean isNotWritable() {
    return !channel.isWritable();
  }

  /**
   * Close the connection
   */
  public void close() {
    // make sure everything is flushed out on close
    endReadAndFlush();
    channel.close();
  }

  public void doPause() {
    channel.config().setAutoRead(false);
  }

  public void doResume() {
    channel.config().setAutoRead(true);
  }

  public void doSetWriteQueueMaxSize(int size) {
    ChannelConfig config = channel.config();
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


  protected ContextImpl getContext() {
    return context;
  }

  protected abstract Object metric();

  protected synchronized void handleException(Throwable t) {
    metrics.exceptionOccurred(metric(), remoteAddress(), t);
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    } else {
      log.error(t);
    }
  }

  protected synchronized void handleClosed() {
    if (metrics instanceof TCPMetrics) {
      ((TCPMetrics) metrics).disconnected(metric(), remoteAddress());
    }
    if (closeHandler != null) {
      closeHandler.handle(null);
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
    if (metrics.isEnabled()) {
      metrics.bytesRead(metric(), remoteAddress(), numberOfBytes);
    }
  }

  public void reportBytesWritten(long numberOfBytes) {
    if (metrics.isEnabled()) {
      metrics.bytesWritten(metric(), remoteAddress(), numberOfBytes);
    }
  }

  private boolean isSSL() {
    return channel.pipeline().get(SslHandler.class) != null;
  }

  protected ChannelFuture sendFile(RandomAccessFile raf, long offset, long length) throws IOException {
    // Write the content.
    ChannelFuture writeFuture;
    if (!supportsFileRegion()) {
      // Cannot use zero-copy
      writeFuture = writeToChannel(new ChunkedFile(raf, offset, length, 8192));
    } else {
      // No encryption - use zero-copy.
      FileRegion region = new DefaultFileRegion(raf.getChannel(), offset, length);
      writeFuture = writeToChannel(region);
    }
    if (writeFuture != null) {
      writeFuture.addListener(fut -> raf.close());
    } else {
      raf.close();
    }
    return writeFuture;
  }

  public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
    if (isSSL()) {
      ChannelHandlerContext sslHandlerContext = channel.pipeline().context("ssl");
      assert sslHandlerContext != null;
      SslHandler sslHandler = (SslHandler) sslHandlerContext.handler();
      return sslHandler.engine().getSession().getPeerCertificateChain();
    } else {
      return null;
    }
  }

  public SocketAddress remoteAddress() {
    InetSocketAddress addr = (InetSocketAddress) channel.remoteAddress();
    if (addr == null) return null;
    return new SocketAddressImpl(addr.getPort(), addr.getAddress().getHostAddress());
  }

  public SocketAddress localAddress() {
    InetSocketAddress addr = (InetSocketAddress) channel.localAddress();
    if (addr == null) return null;
    return new SocketAddressImpl(addr.getPort(), addr.getAddress().getHostAddress());
  }
}
