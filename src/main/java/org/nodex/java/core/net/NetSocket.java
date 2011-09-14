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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.util.CharsetUtil;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.streams.ReadStream;
import org.nodex.java.core.streams.WriteStream;

import java.io.File;
import java.nio.charset.Charset;

/**
 * <p>Represents the interface to a TCP or SSL connection on either the client or the server side.</p>
 * <p>Instances of this class is created on the client side by an {@link NetClient} when a connection to a server is
 * made, or on the server side by a {@link NetServer} when a server accepts a connection.</p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetSocket extends ConnectionBase implements ReadStream, WriteStream {

  private EventHandler<Buffer> dataHandler;
  private EventHandler<Void> endHandler;
  private EventHandler<Void> drainHandler;

  /**
   * When a {@code NetSocket} is created it automatically registers an event handler with the system, the ID of that
   * handler is given by {@code writeHandlerID}.<p>
   * Given this ID, a different event loop can send a buffer to that event handler using {@link Nodex#sendToHandler} and
   * that buffer will be received by this instance in its own event loop and writing to the underlying connection. This
   * allows you to write data to other connections which are owned by different event loops.
   */
  public final long writeHandlerID;

  NetSocket(Channel channel, long contextID, Thread th) {
    super(channel, contextID, th);
    writeHandlerID = Nodex.instance.registerHandler(new EventHandler<Buffer>() {
      public void onEvent(Buffer buff) {
        writeBuffer(buff);
      }
    });
  }

  /**
   * Write a {@link Buffer} to the connection.
   */
  public void writeBuffer(Buffer data) {
    doWrite(data.getChannelBuffer());
  }

  /**
   * Write a {@link Buffer} to the request body.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public NetSocket write(Buffer data) {
    doWrite(data.getChannelBuffer());
    return this;
  }

  /**
   * Write a {@link String} to the connection, encoded in UTF-8.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public NetSocket write(String str) {
    doWrite(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  /**
   * Write a {@link String} to the connection, encoded using the encoding {@code enc}.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      doWrite(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
    }
    return this;
  }

  /**
   * Write a {@link Buffer} to the connection. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public NetSocket write(Buffer data, EventHandler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(data.getChannelBuffer()));
    return this;
  }

  /**
   * Write a {@link String} to the connection, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public NetSocket write(String str, EventHandler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8)));
    return this;
  }

  /**
   * Write a {@link String} to the connection, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public NetSocket write(String str, String enc, EventHandler<Void> doneHandler) {
    if (enc == null) {
      write(str, enc);
    } else {
      addFuture(doneHandler, doWrite(ChannelBuffers.copiedBuffer(str, Charset.forName(enc))));
    }
    return this;
  }

  /**
   * Specify a data handler for the connection. As data is read from the connection the handler will be called.
   */
  public void dataHandler(EventHandler<Buffer> dataHandler) {
    checkThread();
    this.dataHandler = dataHandler;
  }

  /**
   * Specify an end handler for the connection. This will be called when the connection has ended, i.e. when it has been closed.
   */
  public void endHandler(EventHandler<Void> endHandler) {
    checkThread();
    this.endHandler = endHandler;
  }

  /**
   * This method sets a drain handler {@code drainHandler} on the connection. The drain handler will be called when write queue is no longer
   * full and it is safe to write to it again.<p>
   * The drain handler is actually called when the write queue size reaches <b>half</b> the write queue max size to prevent thrashing.
   * This method is used as part of a flow control strategy, e.g. it is used by the {@link org.nodex.java.core.streams.Pump} class to pump data
   * between different streams.
   */
  public void drainHandler(EventHandler<Void> drainHandler) {
    checkThread();
    this.drainHandler = drainHandler;
    callDrainHandler(); //If the channel is already drained, we want to call it immediately
  }

  /**
   * Tell the kernel to stream a file as specified by {@code filename} directly from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system. This is a very efficient way to stream files.<p>
   */
  public void sendFile(String filename) {
    checkThread();
    File f = new File(filename);
    super.sendFile(f);
  }

  protected long getContextID() {
    return super.getContextID();
  }

  protected void handleClosed() {
    super.handleClosed();
    setContextID();
    Nodex.instance.unregisterHandler(writeHandlerID);
    if (endHandler != null) {
      try {
        endHandler.onEvent(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  protected void handleException(Exception e) {
    super.handleException(e);
  }

  void handleInterestedOpsChanged() {
    setContextID();
    callDrainHandler();
  }

  void handleDataReceived(Buffer data) {
    if (dataHandler != null) {
      setContextID();
      try {
        dataHandler.onEvent(data);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }


  //Close without checking thread - used when server is closed
  void internalClose() {
    channel.close();
  }

  private ChannelFuture doWrite(ChannelBuffer buff) {
    checkThread();
    return channel.write(buff);
  }

  private void callDrainHandler() {
    if (drainHandler != null) {
      if ((channel.getInterestOps() & Channel.OP_WRITE) == Channel.OP_WRITE) {
        try {
          drainHandler.onEvent(null);
        } catch (Throwable t) {
          handleHandlerException(t);
        }
      }
    }
  }
}

