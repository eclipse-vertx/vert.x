package org.nodex.core.net;

import java.io.IOException;
import org.jboss.netty.channel.*;
import org.jboss.netty.buffer.*;

public class JavaNetSocket {
   private Channel channel;
   private SocketCallback callback;
   public JavaNetSocket(Channel channel) {
     this.channel = channel;
   }
   
   public void write(String data) {
     try
     {
       //TEMP hack - convert to message
       ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(data.getBytes("UTF-8"));
       channel.write(buffer);
     } catch (IOException ignore) {
     }
   }
   
   public void set_callback(SocketCallback callback) {
     this.callback = callback;
   }
   
   public void dataReceived(String data) {
     callback.data_received(data);
   }

}

