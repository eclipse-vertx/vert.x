package org.nodex.core.net;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.nodex.core.Nodex;
import org.nodex.core.NodexImpl;
import org.nodex.core.NodexInternal;
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
public class NetClient extends NetBase {

  private ClientBootstrap bootstrap;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<Channel, NetSocket>();
  private Map<String, Object> connectionOptions = new HashMap<String, Object>();

  private NetClient() {
    bootstrap = new ClientBootstrap(
        new NioClientSocketChannelFactory(
            NodexInternal.instance.getAcceptorPool(),
            NodexInternal.instance.getWorkerPool()));

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
          final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();
          runOnCorrectThread(ch, new Runnable() {
            public void run() {
              String contextID = NodexInternal.instance.createContext(ch.getWorker());
              NetSocket sock = new NetSocket(ch, contextID, Thread.currentThread());
              socketMap.put(ch, sock);
              connectHandler.onConnect(sock);
            }
          });
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
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      socketMap.remove(ch);
      if (sock != null) {
        runOnCorrectThread(ch, new Runnable() {
          public void run() {
            sock.handleClosed();
            NodexInternal.instance.destroyContext(sock.getContextID());
          }
        });
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      NetSocket sock = socketMap.get(ctx.getChannel());
      if (sock != null) {
        ChannelBuffer cb = (ChannelBuffer) e.getMessage();
        sock.handleDataReceived(new Buffer(cb));
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      ChannelState state = e.getState();
      if (state == ChannelState.INTEREST_OPS) {
        runOnCorrectThread(ch, new Runnable() {
          public void run() {
            sock.handleInterestedOpsChanged();
          }
        });
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      final Throwable t = e.getCause();
      if (sock != null && t instanceof Exception) {
        runOnCorrectThread(ch, new Runnable() {
          public void run() {
            sock.handleException((Exception) t);
            ch.close();
          }
        });
      } else {
        t.printStackTrace();
      }
    }
  }

}
