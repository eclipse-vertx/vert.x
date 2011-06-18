package org.nodex.core.net;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.*;
import org.jboss.netty.bootstrap.*;
import org.jboss.netty.buffer.*;
import java.util.*;
import java.util.concurrent.*;

public class JavaNetServer {

  private ServerCallback callback;
  
  private ServerBootstrap bootstrap;

  public JavaNetServer(ServerCallback callback) {
  
    ChannelFactory factory =
      new NioServerSocketChannelFactory(
              Executors.newCachedThreadPool(),
              Executors.newCachedThreadPool());

    bootstrap = new ServerBootstrap(factory);

    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        public ChannelPipeline getPipeline() {
            return Channels.pipeline(new ServerChannelHandler());
        }
    });

    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    
    this.callback = callback;
  }
  
  private Map<Channel, JavaNetSocket> socketMap = new ConcurrentHashMap<Channel, JavaNetSocket>();
  
  public void listen(int port, String host) {
    bootstrap.bind(new InetSocketAddress(port));
  }
  
  class ServerChannelHandler extends SimpleChannelHandler {
  
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel ch = e.getChannel();
      JavaNetSocket sock = new JavaNetSocket(ch);
      socketMap.put(ch, sock);
      callback.on_connect(sock);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      JavaNetSocket sock = socketMap.get(ch);
      String str = ((ChannelBuffer)e.getMessage()).toString("UTF-8");
      sock.dataReceived(str);
    } 

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      e.getCause().printStackTrace();
      Channel ch = e.getChannel();
      ch.close();
    }
  }  
  
 
}
