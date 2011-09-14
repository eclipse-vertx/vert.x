/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.net;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.nodex.java.core.Handler;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.streams.ReadStream;
import org.nodex.java.core.streams.WriteStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * <p>Abstract base class for TCP connections.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ConnectionBase {

  protected ConnectionBase(Channel channel, long contextID, Thread th) {
    this.channel = channel;
    this.contextID = contextID;
    this.th = th;
  }

  protected final Channel channel;
  protected final long contextID;
  //For sanity checks
  protected final Thread th;

  protected Handler<Exception> exceptionHandler;
  protected Handler<Void> closedHandler;

  /**
   * Pause the connection, see {@link ReadStream#pause}
   */
  public void pause() {
    checkThread();
    channel.setReadable(false);
  }

  /**
   * Resume the connection, see {@link ReadStream#resume}
   */
  public void resume() {
    checkThread();
    channel.setReadable(true);
  }

  /**
   * Set the max size for the write queue, see {@link WriteStream#setWriteQueueMaxSize}
   */
  public void setWriteQueueMaxSize(int size) {
    checkThread();
    NioSocketChannelConfig conf = (NioSocketChannelConfig) channel.getConfig();
    conf.setWriteBufferLowWaterMark(size / 2);
    conf.setWriteBufferHighWaterMark(size);
  }

  /**
   * Is the write queue full?, see {@link WriteStream#writeQueueFull}
   */
  public boolean writeQueueFull() {
    checkThread();
    return !channel.isWritable();
  }

  /**
   * Close the connection
   */
  public void close() {
    checkThread();
    channel.close();
  }

  /**
   * Set an exception handler on the connection
   */
  public void exceptionHandler(Handler<Exception> handler) {
    checkThread();
    this.exceptionHandler = handler;
  }

  /**
   * Set a closed handler on the connection
   */
  public void closedHandler(Handler<Void> handler) {
    checkThread();
    this.closedHandler = handler;
  }

  protected long getContextID() {
    return contextID;
  }

  protected void handleException(Exception e) {
    if (exceptionHandler != null) {
      setContextID();
      try {
        exceptionHandler.handle(e);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    } else {
      handleHandlerException(e);
    }
  }

  protected void handleClosed() {
    if (closedHandler != null) {
      setContextID();
      try {
        closedHandler.handle(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  protected void addFuture(final Handler<Void> doneHandler, final ChannelFuture future) {
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(final ChannelFuture channelFuture) throws Exception {
        setContextID();
        if (channelFuture.isSuccess()) {
          doneHandler.handle(null);
        } else {
          Throwable err = channelFuture.getCause();
          if (exceptionHandler != null && err instanceof Exception) {
            exceptionHandler.handle((Exception) err);
          } else {
            err.printStackTrace();
          }
        }
      }
    });
  }

  protected void checkThread() {
    // All ops must always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread, actual: " + Thread.currentThread() + " expected: " + th);
    }
  }

  protected void setContextID() {
    checkThread();
    NodexInternal.instance.setContextID(contextID);
  }

  protected void handleHandlerException(Throwable t) {
    //We log errors otherwise they will get swallowed
    //TODO logging
    t.printStackTrace(System.err);
  }

  protected boolean isSSL() {
    return channel.getPipeline().get(SslHandler.class) != null;
  }

  protected ChannelFuture sendFile(File file) {
    RandomAccessFile raf;
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
      return writeFuture;
    } catch (IOException e) {
      handleException(e);
      return null;
    }
  }
}
