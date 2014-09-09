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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;

import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;

/**
 * Abstract base class for TCP connections.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ConnectionBase {

  private static final Logger log = LoggerFactory.getLogger(ConnectionBase.class);

  protected ConnectionBase(VertxInternal vertx, Channel channel, ContextImpl context) {
    this.vertx = vertx;
    this.channel = channel;
    this.context = context;
  }

  protected final VertxInternal vertx;
  protected final Channel channel;
  protected final ContextImpl context;

  protected Handler<Throwable> exceptionHandler;
  protected Handler<Void> closeHandler;
  private boolean writable = true;

  private boolean read;
  private boolean needsFlush;

  protected void checkContext() {
    // Sanity check
    if (context != vertx.getContext()) {
      throw new IllegalStateException("Wrong context!");
    }
  }

  public final void startRead() {
    checkContext();
    read = true;
  }

  public final void endReadAndFlush() {
    read = false;
    if (needsFlush) {
      needsFlush = false;
      // flush now
      channel.flush();
    }
  }

  public ChannelFuture queueForWrite(final Object obj) {
    needsFlush = true;
    return channel.write(obj);
  }

  public ChannelFuture writeToChannel(Object obj) {
    if (read) {
      return queueForWrite(obj);
    }
    if (channel.isOpen()) {
      return channel.writeAndFlush(obj);
    } else {
      return null;
    }
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
    channel.config().setWriteBufferLowWaterMark(size / 2);
    channel.config().setWriteBufferHighWaterMark(size);
  }

  public boolean isNotWritable() {
    return !writable;
  }

  protected void setWritable(boolean writable) {
    this.writable = writable;
  }

  protected ContextImpl getContext() {
    return context;
  }

  protected void handleException(Throwable t) {
    if (exceptionHandler != null) {
      context.execute(() -> exceptionHandler.handle(t), false);
    } else {
      log.error(t);
    }
  }

  protected void handleClosed() {
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  protected void addFuture(final Handler<AsyncResult<Void>> completionHandler, final ChannelFuture future) {
    if (future != null) {
      future.addListener(channelFuture -> {
        if (completionHandler != null) {
          context.execute(() -> {
            if (channelFuture.isSuccess()) {
              completionHandler.handle(Future.completedFuture());
            } else {
              completionHandler.handle(Future.completedFuture(channelFuture.cause()));
            }
          }, true);
        } else if (!channelFuture.isSuccess()) {
          handleException(channelFuture.cause());
        }
      });
    }
  }

  protected boolean supportsFileRegion() {
    return !isSSL();
  }

  private boolean isSSL() {
    return channel.pipeline().get(SslHandler.class) != null;
  }

  protected ChannelFuture sendFile(File file) {
    final RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(file, "r");
      long fileLength = file.length();

      // Write the content.
      ChannelFuture writeFuture;
      if (!supportsFileRegion()) {
        // Cannot use zero-copy
        writeFuture = writeToChannel(new ChunkedFile(raf, 0, fileLength, 8192));
      } else {
        // No encryption - use zero-copy.
        final FileRegion region =
            new DefaultFileRegion(raf.getChannel(), 0, fileLength);
        writeFuture = writeToChannel(region);
      }
      writeFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          raf.close();
        }
      });
      return writeFuture;
    } catch (IOException e) {
      handleException(e);
      return null;
    }
  }

  public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
    if (isSSL()) {
      final ChannelHandlerContext sslHandlerContext = channel.pipeline().context("ssl");
      assert sslHandlerContext != null;
      final SslHandler sslHandler = (SslHandler) sslHandlerContext.handler();
      return sslHandler.engine().getSession().getPeerCertificateChain();
    } else {
      return null;
    }
  }

  public SocketAddress remoteAddress() {
    InetSocketAddress addr = (InetSocketAddress)channel.remoteAddress();
    return new SocketAddressImpl(addr.getPort(), addr.getAddress().getHostAddress());
  }

  public SocketAddress localAddress() {
    InetSocketAddress addr = (InetSocketAddress)channel.localAddress();
    return new SocketAddressImpl(addr.getPort(), addr.getAddress().getHostAddress());
  }



  protected abstract void handleInterestedOpsChanged();
}
