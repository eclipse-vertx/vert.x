package org.nodex.core.net;

import org.jboss.netty.channel.Channel;
import org.nodex.core.buffer.Buffer;

public class Socket {
   private Channel channel;

   public Socket(Channel channel) {
     this.channel = channel;
   }
   
   public void write(Buffer data) {
     channel.write(data._toChannelBuffer());
   }

}

