package org.nodex.core.net;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.nodex.core.Nodex;
import org.nodex.core.buffer.Buffer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: timfox
 * Date: 26/06/2011
 * Time: 08:41
 */
public class NetClient {

  //Singleton
  private static NetClient client = new NetClient();

  private ClientBootstrap bootstrap;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<Channel, NetSocket>();

  public static void connect(int port, String host, NetConnectHandler connectHandler) {
    client.doConnect(port, host, connectHandler);
  }

  public static void connect(int port, NetConnectHandler connectHandler) {
    client.doConnect(port, "localhost", connectHandler);
  }

  private void doConnect(int port, String host, final NetConnectHandler connectHandler) {
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (channelFuture.isSuccess()) {
          Channel ch = channelFuture.getChannel();
          NetSocket sock = new NetSocket(ch);
          socketMap.put(ch, sock);
          connectHandler.onConnect(sock);
        }
      }
    });
  }

  private NetClient() {
    bootstrap = new ClientBootstrap(
        new NioClientSocketChannelFactory(
            Nodex.instance.getAcceptorPool(),
            Nodex.instance.getCorePool(),
            Nodex.instance.getCoreThreadPoolSize()));

    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(new ClientHandler());
      }
    });
  }

  private class ClientHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      socketMap.remove(ctx.getChannel());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      NetSocket sock = socketMap.get(ctx.getChannel());
      if (sock != null) {
        ChannelBuffer cb = (ChannelBuffer) e.getMessage();
        sock.dataReceived(new Buffer(cb));
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      e.getChannel().close();
    }
  }

}
