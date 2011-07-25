package org.nodex.core;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;

import java.util.Map;

/**
 * User: timfox
 * Date: 24/07/2011
 * Time: 17:32
 */
public class ConnectionBase {

  protected ConnectionBase(Channel channel, String contextID, Thread th) {
    this.channel = channel;
    this.contextID = contextID;
    this.th = th;
  }

  protected final Channel channel;
  protected final String contextID;
  //For sanity checks
  protected final Thread th;

  protected ExceptionHandler exceptionHandler;
  protected DoneHandler closedHandler;

  public String getContextID() {
    return contextID;
  }

  public void exception(ExceptionHandler handler) {
    this.exceptionHandler = handler;
  }

  public void closed(DoneHandler handler) {
    this.closedHandler = handler;
  }

  public void handleException(Exception e) {
    if (exceptionHandler != null) {
      setContextID();
      exceptionHandler.onException(e);
    } else {
      e.printStackTrace(System.err);
    }
  }

  public void handleClosed() {
    if (closedHandler != null) {
      setContextID();
      closedHandler.onDone();
    }
  }

  public void pause() {
    channel.setReadable(false);
  }

  public void resume() {
    channel.setReadable(true);
  }

  public void setWriteQueueMaxSize(int size) {
    NioSocketChannelConfig conf = (NioSocketChannelConfig) channel.getConfig();
    conf.setWriteBufferLowWaterMark(size / 2);
    conf.setWriteBufferHighWaterMark(size);
  }

  public boolean writeQueueFull() {
    return !channel.isWritable();
  }

  public void close() {
    channel.close();
  }

  public void addFuture(final DoneHandler done, final ChannelFuture future) {
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(final ChannelFuture channelFuture) throws Exception {
        setContextID();
        if (channelFuture.isSuccess()) {
          done.onDone();
        } else {
          Throwable err = channelFuture.getCause();
          if (exceptionHandler != null && err instanceof Exception) {
            exceptionHandler.onException((Exception) err);
          } else {
            err.printStackTrace();
          }
        }
      }
    });
  }

  protected void setContextID() {
    // Sanity check
    // All ops should always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread");
    }
    NodexInternal.instance.setContextID(contextID);
  }


}
