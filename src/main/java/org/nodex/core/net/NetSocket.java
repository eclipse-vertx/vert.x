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

package org.nodex.core.net;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.CharsetUtil;
import org.nodex.core.ConnectionBase;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

import java.nio.charset.Charset;

public class NetSocket extends ConnectionBase implements ReadStream, WriteStream {

  private DataHandler dataHandler;
  private Runnable drainHandler;

  NetSocket(Channel channel, String contextID, Thread th) {
    super(channel, contextID, th);
  }

  public void writeBuffer(Buffer data) {
    channel.write(data._toChannelBuffer());
  }

  public NetSocket write(Buffer data) {
    channel.write(data._toChannelBuffer());
    return this;
  }

  public NetSocket write(String str) {
    channel.write(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  public NetSocket write(String str, String enc) {
    channel.write(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
    return this;
  }

  public NetSocket write(Buffer data, final Runnable done) {
    addFuture(done, channel.write(data._toChannelBuffer()));
    return this;
  }

  public NetSocket write(String str, Runnable done) {
    addFuture(done, channel.write(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8)));
    return this;
  }

  public NetSocket write(String str, String enc, Runnable done) {
    addFuture(done, channel.write(ChannelBuffers.copiedBuffer(str, Charset.forName(enc))));
    return this;
  }

  public void data(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void drain(Runnable drained) {
    this.drainHandler = drained;
    callDrainHandler(); //If the channel is already drained, we want to call it immediately
  }

  protected void handleClosed() {
    super.handleClosed();
  }

  protected String getContextID() {
    return super.getContextID();
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
        dataHandler.onData(data);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  private void callDrainHandler() {
    if (drainHandler != null) {
      if ((channel.getInterestOps() & Channel.OP_WRITE) == Channel.OP_WRITE) {
        try {
          drainHandler.run();
        } catch (Throwable t) {
          handleHandlerException(t);
        }
      }
    }
  }
}

