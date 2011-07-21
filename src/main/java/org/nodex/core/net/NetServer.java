package org.nodex.core.net;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.nodex.core.DoneHandler;
import org.nodex.core.Nodex;
import org.nodex.core.NodexImpl;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetServer extends NetBase {
  private ServerBootstrap bootstrap;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<Channel, NetSocket>();
  private final NetConnectHandler connectCallback;
  private Map<String, Object> connectionOptions = new HashMap<String, Object>();
  private ChannelGroup serverChannelGroup;

  private NetServer(NetConnectHandler connectHandler) {
    serverChannelGroup = new DefaultChannelGroup("nodex-acceptor-channels");

    ChannelFactory factory =
        new NioServerSocketChannelFactory(
            NodexInternal.instance.getAcceptorPool(),
            NodexInternal.instance.getWorkerPool());
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() {
        return Channels.pipeline(new ServerHandler());
      }
    });

    //Defaults
    connectionOptions.put("child.tcpNoDelay", true);
    connectionOptions.put("child.keepAlive", true);

    this.connectCallback = connectHandler;
  }

  // Public API ========================================================================================================

  public static NetServer createServer(NetConnectHandler connectCallback) {
    return new NetServer(connectCallback);
  }

  public NetServer setTcpNoDelay(boolean tcpNoDelay) {
    connectionOptions.put("child.tcpNoDelay", tcpNoDelay);
    return this;
  }

  public NetServer setSendBufferSize(int size) {
    connectionOptions.put("child.sendBufferSize", size);
    return this;
  }

  public NetServer setReceiveBufferSize(int size) {
    connectionOptions.put("child.receiveBufferSize", size);
    return this;
  }

  public NetServer setKeepAlive(boolean keepAlive) {
    connectionOptions.put("child.keepAlive", keepAlive);
    return this;
  }

  public NetServer setReuseAddress(boolean reuse) {
    connectionOptions.put("child.reuseAddress", reuse);
    return this;
  }

  public NetServer setSoLinger(boolean linger) {
    connectionOptions.put("child.soLinger", linger);
    return this;
  }

  public NetServer setTrafficClass(int trafficClass) {
    connectionOptions.put("child.trafficClass", trafficClass);
    return this;
  }

  public NetServer listen(int port) {
    return listen(port, "0.0.0.0");
  }

  public NetServer listen(int port, String host) {
    try {
      bootstrap.setOptions(connectionOptions);
      //FIXME - currently bootstrap.bind is blocking - need to make it non blocking by not using bootstrap directly
      Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
      serverChannelGroup.add(serverChannel);
      System.out.println("Net server listening on " + host + ":" + port);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return this;
  }

  public void close() {
    close(null);
  }

  public void close(final DoneHandler done) {
    for (NetSocket sock : socketMap.values()) {
      sock.close();
    }
    if (done != null) {
      serverChannelGroup.close().addListener(new ChannelGroupFutureListener() {
        public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
          done.onDone();
        }
      });
    }
  }

  // End of public API =================================================================================================

  private class ServerHandler extends SimpleChannelHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      runOnCorrectThread(ch, new Runnable() {
        public void run() {
          String contextID = NodexInternal.instance.createContext(ch.getWorker());
          NetSocket sock = new NetSocket(ch, contextID, Thread.currentThread());
          socketMap.put(ch, sock);
          connectCallback.onConnect(sock);
        }
      });
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
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      socketMap.remove(ch);
      runOnCorrectThread(ch, new Runnable() {
        public void run() {
          sock.handleClosed();
          NodexInternal.instance.destroyContext(sock.getContextID());
        }
      });
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      NetSocket sock = socketMap.get(ch);
      ChannelBuffer buff = (ChannelBuffer) e.getMessage();
      ChannelBuffer sliced = buff.slice();
      sock.handleDataReceived(new Buffer(sliced));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      ch.close();
      final Throwable t = e.getCause();
      if (sock != null && t instanceof Exception) {
        runOnCorrectThread(ch, new Runnable() {
          public void run() {
            sock.handleException((Exception) t);
          }
        });
      } else {
        t.printStackTrace();
      }
    }
  }
}
