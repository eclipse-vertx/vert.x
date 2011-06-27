package org.nodex.core.net;

import org.jboss.netty.channel.Channel;
import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

public class Socket {
   private Channel channel;
   private Callback<Buffer> dataCallback;

   protected Socket(Channel channel) {
     this.channel = channel;
   }
   
   public void write(Buffer data) {
     channel.write(data._toChannelBuffer());
   }

   public void data(Callback<Buffer> dataCallback) {
     this.dataCallback = dataCallback;
   }

   protected void dataReceived(Buffer data) {
     dataCallback.onEvent(data);
   }
}

