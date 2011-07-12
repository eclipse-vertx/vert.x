package org.nodex.core.net;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.util.CharsetUtil;
import org.nodex.core.DoneHandler;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

import java.nio.charset.Charset;

public class NetSocket {
  private final Channel channel;
  private volatile DataHandler dataHandler;
  private volatile ExceptionHandler exceptionHandler;
  private DoneHandler drainHandler;
  private DoneHandler closedHandler;

  NetSocket(Channel channel) {
    this.channel = channel;
  }

  // Public API ========================================================================================================

  public void write(Buffer data) {
    channel.write(data._toChannelBuffer());
  }

  public void write(String str) {
    channel.write(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8));
  }

  public void write(String str, String enc) {
    channel.write(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
  }

  public void write(Buffer data, final DoneHandler done) {
    addFuture(done, channel.write(data._toChannelBuffer()));
  }

  public void write(String str, DoneHandler done) {
    addFuture(done, channel.write(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8)));
  }

  public void write(String str, String enc, DoneHandler done) {
    addFuture(done, channel.write(ChannelBuffers.copiedBuffer(str, Charset.forName(enc))));
  }

  public void data(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void exception(ExceptionHandler handler) {
    this.exceptionHandler = handler;
  }

  public void drain(DoneHandler drained) {
    this.drainHandler = drained;
  }

  public synchronized void closed(DoneHandler closed) {
    this.closedHandler = closed;
    if (!channel.isOpen()) {
      closedHandler.onDone(); //Call it now if already closed
    }
  }

  public void pause() {
    channel.setReadable(false);
  }

  public void resume() {
    channel.setReadable(true);
  }

  //Default is 64kB
  public void setWriteQueueMaxSize(int size) {
    NioSocketChannelConfig conf =  (NioSocketChannelConfig)channel.getConfig();
    conf.setWriteBufferHighWaterMark(size);
    conf.setWriteBufferLowWaterMark(size / 2);
  }

  public void close() {
    channel.close();
  }

  public boolean writeQueueFull() {
    return !channel.isWritable();
  }

  // End of public API =================================================================================================

  void interestOpsChanged() {
    if (channel.isWritable() && drainHandler != null) {
      drainHandler.onDone();
    }
  }

  void dataReceived(Buffer data) {
    try {
      if (dataHandler != null) {
        dataHandler.onData(data);
      }
    } catch (Throwable t) {
      //We log errors otherwise they will get swallowed
      //TODO logging can be improved
      t.printStackTrace();
      if (t instanceof RuntimeException) {
        throw (RuntimeException) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      }
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.onException(e);
    }
  }

  synchronized void handleClosed() {
    if (closedHandler != null) {
      closedHandler.onDone();
    }
  }

  private void addFuture(final DoneHandler done, final ChannelFuture future) {
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (channelFuture.isSuccess()) {
          done.onDone();
        } else {
          if (exceptionHandler != null) {
            Throwable err = channelFuture.getCause();
            if (err instanceof Exception) {
              exceptionHandler.onException((Exception)err);
            } else {
              err.printStackTrace();
            }
          }
        }
      }
    });
  }

}

