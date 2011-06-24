package org.nodex.core.net;

import org.jboss.netty.channel.Channel;
import org.nodex.core.buffer.JavaBuffer;

public class JavaNetSocket {
   private Channel channel;
   private SocketCallback callback;
   public JavaNetSocket(Channel channel) {
     this.channel = channel;
   }
   
   public void write(JavaBuffer data) {
     channel.write(data._toChannelBuffer());
   }
   
   public void set_callback(SocketCallback callback) {
     this.callback = callback;
   }
   
   public void dataReceived(JavaBuffer data) {
     callback.data_received(data);
   }

}

