/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.java.core.net;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.util.CharsetUtil;
import org.nodex.java.core.ConnectionBase;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.streams.ReadStream;
import org.nodex.java.core.streams.WriteStream;

import java.io.File;
import java.nio.charset.Charset;

public class NetSocket extends ConnectionBase implements ReadStream, WriteStream {

  private EventHandler<Buffer> dataHandler;
  private EventHandler<Void> endHandler;
  private EventHandler<Void> drainHandler;

  public final long writeHandlerID;

  NetSocket(Channel channel, long contextID, Thread th) {
    super(channel, contextID, th);
    writeHandlerID = Nodex.instance.registerHandler(new EventHandler<Buffer>() {
      public void onEvent(Buffer buff) {
        writeBuffer(buff);
      }
    });
  }

  public void writeBuffer(Buffer data) {
    doWrite(data.getChannelBuffer());
  }

  public NetSocket write(Buffer data) {
    doWrite(data.getChannelBuffer());
    return this;
  }

  public NetSocket write(String str) {
    doWrite(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      doWrite(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
    }
    return this;
  }

  public NetSocket write(Buffer data, EventHandler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(data.getChannelBuffer()));
    return this;
  }

  public NetSocket write(String str, EventHandler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8)));
    return this;
  }

  public NetSocket write(String str, String enc, EventHandler<Void> doneHandler) {
    if (enc == null) {
      write(str, enc);
    } else {
      addFuture(doneHandler, doWrite(ChannelBuffers.copiedBuffer(str, Charset.forName(enc))));
    }
    return this;
  }

  public void dataHandler(EventHandler<Buffer> dataHandler) {
    checkThread();
    this.dataHandler = dataHandler;
  }

  public void endHandler(EventHandler<Void> endHandler) {
    checkThread();
    this.endHandler = endHandler;
  }

  public void drainHandler(EventHandler<Void> drainHandler) {
    checkThread();
    this.drainHandler = drainHandler;
    callDrainHandler(); //If the channel is already drained, we want to call it immediately
  }

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

