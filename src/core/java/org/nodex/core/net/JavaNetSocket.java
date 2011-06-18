package org.nodex.core.net;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.*;
import org.jboss.netty.bootstrap.*;
import org.jboss.netty.buffer.*;

public class JavaNetSocket {
   private Channel channel;
   private SocketCallback callback;
   public JavaNetSocket(Channel channel) {
     this.channel = channel;
   }
   
   public void write(String data) {
     channel.write(data);
   }
   
   public void set_callback(SocketCallback callback) {
     this.callback = callback;
   }
   
   public void dataReceived(String data) {
     System.out.println("calling ruby data_received");
     callback.data_received(data);
   }

}

