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
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VoidResult;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.FlowControlHandler;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

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

  protected ConnectionBase(VertxInternal vertx, Channel channel, Context context) {
    this.vertx = vertx;
    this.channel = channel;
    this.context = context;
  }

  protected final VertxInternal vertx;
  protected final Channel channel;
  protected final Context context;

  protected Handler<Exception> exceptionHandler;
  protected Handler<Void> closedHandler;
  private volatile boolean writable = true;

  /**
   * Close the connection
   */
  public void close() {
    channel.close();
  }

  /**
   * Set a closed handler on the connection
   */
  public void closedHandler(Handler<Void> handler) {
    this.closedHandler = handler;
  }

  public void doPause() {
    channel.config().setAutoRead(false);
  }

  public void doResume() {
    channel.config().setAutoRead(true);
  }

  public void doSetWriteQueueMaxSize(int size) {
    channel.pipeline().get(FlowControlHandler.class).setLimit(size / 2 , size);
  }

  public boolean doWriteQueueFull() {
    return !writable;
  }

  protected void setWritable(boolean writable) {
    this.writable = writable;
  }

  protected Context getContext() {
    return context;
  }

  protected void handleException(Exception e) {
    if (exceptionHandler != null) {
      setContext();
      try {
        exceptionHandler.handle(e);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  protected void handleClosed() {
    if (closedHandler != null) {
      setContext();
      try {
        closedHandler.handle(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  protected void addFuture(final AsyncResultHandler<Void> doneHandler, final ChannelFuture future) {
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(final ChannelFuture channelFuture) throws Exception {
        if (doneHandler != null || exceptionHandler != null) {
          context.execute(new Runnable() {
            public void run() {
              Throwable t = channelFuture.cause();
              if (doneHandler != null) {
                doneHandler.handle(new VoidResult(t));
              }
              // TODO - do we really need to send the exception to the connection exception handler too?
              if (t instanceof Exception) {
                handleException((Exception)t);
              } else {
                vertx.reportException(t);
              }
            }
          });
        }
      }
    });
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
        writeFuture = channel.write(new ChunkedFile(raf, 0, fileLength, 8192));
      } else {
        // No encryption - use zero-copy.
        final FileRegion region =
            new DefaultFileRegion(raf.getChannel(), 0, fileLength);
        writeFuture = channel.write(region);
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
  protected abstract void handleInterestedOpsChanged();
}
