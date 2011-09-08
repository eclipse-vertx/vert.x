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

/**
 * <p>NetClient is an asynchronous factory for TCP or SSL connections</p>
 *
 * <p>Multiple connections to different servers can be made using the same instance. Instances of this class can be shared by different
 * even loops.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClient extends SSLBase {

  private ClientBootstrap bootstrap;
  private NioClientSocketChannelFactory channelFactory;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<>();
  private Map<String, Object> connectionOptions = new HashMap<>();
  private EventHandler<Exception> exceptionHandler;

  /**
   * Create a new {@code NetClient}
   */
  public NetClient() {
    connectionOptions.put("tcpNoDelay", true);
    connectionOptions.put("keepAlive", true);
  }

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP addresss. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
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

  /**
   * Attempt to open a connection to a server at the specific {@code port} and host localhost
   * The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient connect(int port, EventHandler<NetSocket> connectCallback) {
    return connect(port, "localhost", connectCallback);
  }

  /**
   * Close the client. Any sockets which have not been closed manually will be closed here.
   */
  public void close() {
    for (NetSocket sock : socketMap.values()) {
      sock.close();
    }
  }

  /**
   * Set the exception handler. Any exceptions that occur during connect or later on will be notified via the {@code handler}.
   * If no handler is supplied any exceptions will be printed to {@link System#err}
   */
  public void exceptionHandler(EventHandler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  /**
   * If {@code ssl} is {@code true}, this signifies the client will create SSL connections
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setSSL(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * Set the path to the SSL client key store. This method should only be used with the client in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL client key store is a standard Java Key Store, and should contain the client certificate. It's only necessary to supply
   * a client key store if the server requires client authentication via client certificates.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setKeyStorePath(String path) {
    this.keyStorePath = path;
    return this;
  }

  /**
   * Set the password for the SSL client key store. This method should only be used with the client in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setKeyStorePassword(String pwd) {
    this.keyStorePassword = pwd;
    return this;
  }

  /**
   * Set the path to the SSL client trust store. This method should only be used with the client in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL client trust store is a standard Java Key Store, and should contain the certificate(s) of the servers that the client trusts. The SSL
   * handshake will fail if the server provides a certificate that the client does not trust.<p>
   * If you wish the client to trust all server certificates you can use the {@link #setTrustAll(boolean)} method.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setTrustStorePath(String path) {
    this.trustStorePath = path;
    return this;
  }

  /**
   * Set the password for the SSL client trust store. This method should only be used with the client in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setTrustStorePassword(String pwd) {
    this.trustStorePassword = pwd;
    return this;
  }

  /**
   * If {@code trustAll} is set to {@code true} then the client will trust ALL server certifactes and will not attempt to authenticate them
   * against it's local client trust store.<p>
   * Use this method with caution!
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetClient setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setTcpNoDelay(boolean tcpNoDelay) {
    connectionOptions.put("child.tcpNoDelay", tcpNoDelay);
    return this;
  }

  /**
   * Set the TCP send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setSendBufferSize(int size) {
    connectionOptions.put("child.sendBufferSize", size);
    return this;
  }

  /**
   * Set the TCP receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setReceiveBufferSize(int size) {
    connectionOptions.put("child.receiveBufferSize", size);
    return this;
  }

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setKeepAlive(boolean keepAlive) {
    connectionOptions.put("child.keepAlive", keepAlive);
    return this;
  }

  /**
   * Set the TCP reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setReuseAddress(boolean reuse) {
    connectionOptions.put("child.reuseAddress", reuse);
    return this;
  }

  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient setSoLinger(boolean linger) {
    connectionOptions.put("child.soLinger", linger);
    return this;
  }

  /**
   * Set the TCP trafficClass setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
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
