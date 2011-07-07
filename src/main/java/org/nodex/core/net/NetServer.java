package org.nodex.core.net;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.Nodex;
import org.nodex.core.buffer.Buffer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetServer {
  private ServerBootstrap bootstrap;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<Channel, NetSocket>();
  private final NetConnectHandler connectCallback;
  private ExceptionHandler exceptionHandler;

  private NetServer(NetConnectHandler connectCallback) {
    ChannelFactory factory =
        new NioServerSocketChannelFactory(
            Nodex.instance.getAcceptorPool(),
            Nodex.instance.getCorePool(),
            Nodex.instance.getCoreThreadPoolSize());
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() {
        return Channels.pipeline(new ServerHandler());
      }
    });
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    this.connectCallback = connectCallback;
  }

  public static NetServer createServer(NetConnectHandler connectCallback) {
    return new NetServer(connectCallback);
  }

  public NetServer listen(int port) {
    return listen(port, "0.0.0.0");
  }

  public NetServer listen(int port, String host) {
    try {
      bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
      System.out.println("Net server listening on " + host + ":" + port);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return this;
  }

  public void stop() {
    for (NetSocket sock : socketMap.values()) {
      sock.close();
    }
    bootstrap.releaseExternalResources();
  }

  private class ServerHandler extends SimpleChannelHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel ch = e.getChannel();
      NetSocket sock = new NetSocket(ch);
      socketMap.put(ch, sock);
      connectCallback.onConnect(sock);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel ch = e.getChannel();
      socketMap.remove(ch);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      NetSocket sock = socketMap.get(ch);
      sock.dataReceived(new Buffer((ChannelBuffer) e.getMessage()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      Channel ch = e.getChannel();
      ch.close();
      Throwable t = e.getCause();
      if (exceptionHandler != null && t instanceof Exception) {
        exceptionHandler.onException((Exception) t);
      } else {
        t.printStackTrace();
      }
    }
  }
}
