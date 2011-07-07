package org.nodex.core.net;

import org.jboss.netty.channel.Channel;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

public class NetSocket {
  private Channel channel;
  private DataHandler dataHandler;

  protected NetSocket(Channel channel) {
    this.channel = channel;
  }

  public void write(Buffer data) {
    channel.write(data._toChannelBuffer());
  }

  public void data(DataHandler dataHandler) {
    this.dataHandler = dataHandler;
  }

  public void close() {
    channel.close();
  }

  protected void dataReceived(Buffer data) {
    try {
      dataHandler.onData(data);
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
}

