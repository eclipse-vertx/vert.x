/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.java.core.net;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.NodexInternal;
import org.nodex.java.core.SSLBase;
import org.nodex.java.core.ThreadSourceUtils;
import org.nodex.java.core.buffer.Buffer;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetClient extends SSLBase {

  private ClientBootstrap bootstrap;
  private NioClientSocketChannelFactory channelFactory;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<>();
  private Map<String, Object> connectionOptions = new HashMap<>();
  private EventHandler<Exception> exceptionHandler;

  public NetClient() {
    connectionOptions.put("tcpNoDelay", true);
    connectionOptions.put("keepAlive", true);
  }

  public NetClient connect(int port, String host, final EventHandler<NetSocket> connectHandler) {

    final Long contextID = Nodex.instance.getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Requests must be made from inside an event loop");
    }

    if (bootstrap == null) {
      channelFactory = new NioClientSocketChannelFactory(
          NodexInternal.instance.getAcceptorPool(),
          NodexInternal.instance.getWorkerPool());
      bootstrap = new ClientBootstrap(channelFactory);

      checkSSL();

      bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        public ChannelPipeline getPipeline() throws Exception {
          ChannelPipeline pipeline = Channels.pipeline();
          if (ssl) {
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(true); //We are on the client side of the connection
            pipeline.addLast("ssl", new SslHandler(engine));
          }
          pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());  // For large file / sendfile support
          pipeline.addLast("handler", new ClientHandler());
          return pipeline;
        }
      });
    }

    //Client connections share context with caller
    channelFactory.setWorker(NodexInternal.instance.getWorkerForContextID(contextID));

    bootstrap.setOptions(connectionOptions);
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (channelFuture.isSuccess()) {
          final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();

          ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
            public void run() {
              NodexInternal.instance.setContextID(contextID);
              NetSocket sock = new NetSocket(ch, contextID, Thread.currentThread());
              socketMap.put(ch, sock);
              connectHandler.onEvent(sock);
            }
          });
        } else {
          Throwable t = channelFuture.getCause();
          if (t instanceof Exception && exceptionHandler != null) {
            exceptionHandler.onEvent((Exception) t);
          } else {
            t.printStackTrace(System.err);
          }
        }
      }
    });
    return this;
  }

  public NetClient connect(int port, EventHandler<NetSocket> connectCallback) {
    return connect(port, "localhost", connectCallback);
  }

  public void close() {
    for (NetSocket sock : socketMap.values()) {
      sock.close();
    }
  }

  public void exceptionHandler(EventHandler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  public NetClient setSSL(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public NetClient setKeyStorePath(String path) {
    this.keyStorePath = path;
    return this;
  }

  public NetClient setKeyStorePassword(String pwd) {
    this.keyStorePassword = pwd;
    return this;
  }

  public NetClient setTrustStorePath(String path) {
    this.trustStorePath = path;
    return this;
  }

  public NetClient setTrustStorePassword(String pwd) {
    this.trustStorePassword = pwd;
    return this;
  }

  public NetClient setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
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
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
          public void run() {
            sock.handleClosed();
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
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
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
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
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
