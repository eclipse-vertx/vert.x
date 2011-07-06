package org.nodex.core.net;

import org.jboss.netty.channel.Channel;
import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

public class NetSocket {
  private Channel channel;
  private Callback<Buffer> dataCallback;

  protected NetSocket(Channel channel) {
    this.channel = channel;
  }

  public void write(Buffer data) {
    channel.write(data._toChannelBuffer());
  }

  public void data(Callback<Buffer> dataCallback) {
    this.dataCallback = dataCallback;
  }

  public void close() {
    channel.close();
  }

  protected void dataReceived(Buffer data) {
    try {
      dataCallback.onEvent(data);
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

