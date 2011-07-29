package org.nodex.core;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;

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
  protected Runnable closedHandler;

  // Public API -------------------------------------------------------------------------------

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

  // Handlers ---------------------------------------------------------------------

  public void exception(ExceptionHandler handler) {
    this.exceptionHandler = handler;
  }

  public void closed(Runnable handler) {
    this.closedHandler = handler;
  }

  // Impl ?? ----------------------------------------------------------------------------------------------

  protected String getContextID() {
    return contextID;
  }

  protected void handleException(Exception e) {
    if (exceptionHandler != null) {
      setContextID();
      try {
        exceptionHandler.onException(e);
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
        closedHandler.run();
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  protected void addFuture(final Runnable done, final ChannelFuture future) {
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(final ChannelFuture channelFuture) throws Exception {
        setContextID();
        if (channelFuture.isSuccess()) {
          done.run();
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

  protected void handleHandlerException(Throwable t) {
    //We log errors otherwise they will get swallowed
    //TODO logging
    t.printStackTrace(System.err);
  }
}
