package org.nodex.core.net;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.util.CharsetUtil;
import org.nodex.core.ConnectionBase;
import org.nodex.core.DoneHandler;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

import java.nio.charset.Charset;

public class NetSocket extends ConnectionBase implements ReadStream, WriteStream {
  private DataHandler dataHandler;
  private DoneHandler drainHandler;

  NetSocket(Channel channel, String contextID, Thread th) {
    super(channel, contextID, th);
  }

  // Public API ========================================================================================================

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

  public NetSocket write(Buffer data, final DoneHandler done) {
    addFuture(done, channel.write(data._toChannelBuffer()));
    return this;
  }

  public NetSocket write(String str, DoneHandler done) {
    addFuture(done, channel.write(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8)));
    return this;
  }

  public NetSocket write(String str, String enc, DoneHandler done) {
    addFuture(done, channel.write(ChannelBuffers.copiedBuffer(str, Charset.forName(enc))));
    return this;
  }

  public void data(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void drain(DoneHandler drained) {
    this.drainHandler = drained;
    callDrainHandler(); //If the channel is already drained, we want to call it immediately
  }

  // End of public API =================================================================================================

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
          drainHandler.onDone();
        } catch (Throwable t) {
          handleHandlerException(t);
        }
      }
    }
  }
}

