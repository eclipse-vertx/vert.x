package org.nodex.core.net;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.nodex.core.buffer.JavaBuffer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;


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
    try {
        bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
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
      sock.dataReceived(new JavaBuffer((ChannelBuffer)e.getMessage()));
    } 

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      Channel ch = e.getChannel();
      ch.close();
      e.getCause().printStackTrace();
      callback.on_exception(e.getCause());
    }
  }  
  
 
}
