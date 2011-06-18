package org.nodex.core.net;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.*;
import org.jboss.netty.bootstrap.*;
import org.jboss.netty.buffer.*;

public class NettyServer {
  public static void main(String[] args) {
    System.out.println("Starting netty echo server");
    
    ChannelFactory factory =
        new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());

    ServerBootstrap bootstrap = new ServerBootstrap(factory);

    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        public ChannelPipeline getPipeline() {
            return Channels.pipeline(new EchoServerHandler());
        }
    });

    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);

    bootstrap.bind(new InetSocketAddress(8080));
  }
  
  static class EchoServerHandler extends SimpleChannelHandler {
  
    public EchoServerHandler() {
      System.out.println("Creating handler");
    }
    
   

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      ch.write(e.getMessage());
    } 

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      e.getCause().printStackTrace();
      
      Channel ch = e.getChannel();
      ch.close();
    }
  }  
}
