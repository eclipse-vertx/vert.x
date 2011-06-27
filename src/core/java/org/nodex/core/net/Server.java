package org.nodex.core.net;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Server {
  private ServerBootstrap bootstrap;
  private Map<Channel, Socket> socketMap = new ConcurrentHashMap<Channel, Socket>();
  private final Callback<Socket> connectCallback;
  private Callback<Exception> exceptionCallback;

  private Server(Callback<Socket> connectCallback) {
    ChannelFactory factory =
    new NioServerSocketChannelFactory(
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
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

  public static Server createServer(Callback<Socket> connectCallback) {
    return new Server(connectCallback);
  }

  public void listen(int port, String host) {
    try {
        bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
        System.out.println("Net server listening on " + host + ":" + port);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  public void stop() {
  }

  private class ServerHandler extends SimpleChannelHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel ch = e.getChannel();
      Socket sock = new Socket(ch);
      socketMap.put(ch, sock);
      connectCallback.onEvent(sock);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel ch = e.getChannel();
      socketMap.remove(ch);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      Socket sock = socketMap.get(ch);
      sock.dataReceived(new Buffer((ChannelBuffer)e.getMessage()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      Channel ch = e.getChannel();
      ch.close();
      Throwable t = e.getCause();
      if (exceptionCallback != null && t instanceof Exception) {
        exceptionCallback.onEvent((Exception) t);
      } else {
        t.printStackTrace();
      }
    }
  }
}
