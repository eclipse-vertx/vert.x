package org.nodex.core.net;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.nodex.core.Nodex;
import org.nodex.core.buffer.Buffer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: timfox
 * Date: 26/06/2011
 * Time: 08:41
 */
public class NetClient {

  private ClientBootstrap bootstrap;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<Channel, NetSocket>();
  private Map<String, Object> connectionOptions = new HashMap<String, Object>();

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

  // Public API ========================================================================================================

  public static NetClient createClient() {
    return new NetClient();
  }

  public NetClient connect(int port, String host, final NetConnectHandler connectHandler) {
    bootstrap.setOptions(connectionOptions);
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
    return this;
  }

  public NetClient connect(int port, NetConnectHandler connectCallback) {
    return connect(port, "localhost", connectCallback);
  }

  public NetClient setTcpNoDelay(boolean tcpNoDelay) {
    connectionOptions.put("child.tcpNoDelay", tcpNoDelay);
    return this;
  }

  public NetClient setSendBufferSize(int size) {
    connectionOptions.put("child.sendBufferSize", size);
    return this;
  }

  public NetClient setReceiveBufferSize(int size) {
    connectionOptions.put("child.receiveBufferSize", size);
    return this;
  }

  public NetClient setKeepAlive(boolean keepAlive) {
    connectionOptions.put("child.keepAlive", keepAlive);
    return this;
  }

  public NetClient setReuseAddress(boolean reuse) {
    connectionOptions.put("child.reuseAddress", reuse);
    return this;
  }

  public NetClient setSoLinger(boolean linger) {
    connectionOptions.put("child.soLinger", linger);
    return this;
  }

  public NetClient setTrafficClass(int trafficClass) {
    connectionOptions.put("child.trafficClass", trafficClass);
    return this;
  }

  // End of public API =================================================================================================

  private class ClientHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      NetSocket sock = socketMap.get(ctx.getChannel());
      socketMap.remove(ctx.getChannel());
      if (sock != null) {
        sock.handleClosed();
      }
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
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      Channel ch = e.getChannel();
      NetSocket sock = socketMap.get(ch);
      ChannelState state = e.getState();
      if (state == ChannelState.INTEREST_OPS) {
        sock.interestOpsChanged();
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      NetSocket sock = socketMap.get(ctx.getChannel());
      e.getChannel().close();
      Throwable t = e.getCause();
      if (sock != null && t instanceof Exception) {
        sock.handleException((Exception) t);
      } else {
        t.printStackTrace();
      }
    }
  }

}
