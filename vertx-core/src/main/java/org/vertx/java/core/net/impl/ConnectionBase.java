/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import io.netty.channel.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;

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

  protected ConnectionBase(VertxInternal vertx, Channel channel, DefaultContext context) {
    this.vertx = vertx;
    this.channel = channel;
    this.context = context;
  }

  protected final VertxInternal vertx;
  protected final Channel channel;
  protected final DefaultContext context;

  protected Handler<Throwable> exceptionHandler;
  protected Handler<Void> closeHandler;
  private volatile boolean writable = true;

  private boolean read;
  private boolean needsFlush;

  public final void startRead() {
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

  public ChannelFuture write(Object obj) {
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

  public boolean doWriteQueueFull() {
    return !writable;
  }

  protected void setWritable(boolean writable) {
    this.writable = writable;
  }

  protected DefaultContext getContext() {
    return context;
  }

  protected void handleException(Throwable t) {
    if (exceptionHandler != null) {
      setContext();
      try {
        exceptionHandler.handle(t);
      } catch (Throwable t2) {
        handleHandlerException(t2);
      }
    }
  }

  protected void handleClosed() {
    if (closeHandler != null) {
      setContext();
      try {
        closeHandler.handle(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  protected void addFuture(final Handler<AsyncResult<Void>> doneHandler, final ChannelFuture future) {
    if (future != null) {
      future.addListener(new ChannelFutureListener() {
        public void operationComplete(final ChannelFuture channelFuture) throws Exception {
          if (doneHandler != null) {
            context.execute(new Runnable() {
              public void run() {
                if (channelFuture.isSuccess()) {
                  doneHandler.handle(new DefaultFutureResult<>((Void)null));
                } else {
                  doneHandler.handle(new DefaultFutureResult<Void>(channelFuture.cause()));
                }
              }
            });
          } else if (!channelFuture.isSuccess()) {
            vertx.reportException(channelFuture.cause());
          }
        }
      });
    }
  }

  protected void setContext() {
    vertx.setContext(context);
  }

  protected void handleHandlerException(Throwable t) {
    vertx.reportException(t);
  }

  protected boolean isSSL() {
    return channel.pipeline().get(SslHandler.class) != null;
  }

  protected ChannelFuture sendFile(File file) {
    final RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(file, "r");
      long fileLength = file.length();

      // Write the content.
      ChannelFuture writeFuture;
      if (isSSL()) {
        // Cannot use zero-copy with HTTPS.
        writeFuture = write(new ChunkedFile(raf, 0, fileLength, 8192));
      } else {
        // No encryption - use zero-copy.
        final FileRegion region =
            new DefaultFileRegion(raf.getChannel(), 0, fileLength);
        writeFuture = write(region);
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

  public InetSocketAddress remoteAddress() {
    return (InetSocketAddress)channel.remoteAddress();
  }

  public InetSocketAddress localAddress() {
    return (InetSocketAddress) channel.localAddress();
  }

  protected abstract void handleInterestedOpsChanged();
}
