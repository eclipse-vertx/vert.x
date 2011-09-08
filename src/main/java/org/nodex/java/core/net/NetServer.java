/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.net;

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
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.NodexInternal;
import org.nodex.java.core.SSLBase;
import org.nodex.java.core.ThreadSourceUtils;
import org.nodex.java.core.buffer.Buffer;

import javax.net.ssl.SSLEngine;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Encapsulates a server that understands TCP or SSL.</p>
 *
 * <p>Instances of this class can only be used from the event loop that created it. When connections are accepted by the server
 * they are supplied to the user in the form of a {@link NetSocket} instance that is passed via an instance of
 * {@link EventHandler} which is supplied to the server via the {@link #connectHandler} method.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetServer extends SSLBase {

  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap();
  private EventHandler<NetSocket> connectHandler;
  private Map<String, Object> connectionOptions = new HashMap();
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private final Thread th;

  private ClientAuth clientAuth = ClientAuth.NONE;

  /**
   * Create a new NetServer instance.
   */
  public NetServer() {
    if (Nodex.instance.getContextID() == null) {
      throw new IllegalStateException("Net Server can only be used from an event loop");
    }
    this.th = Thread.currentThread();

    //Defaults
    connectionOptions.put("child.tcpNoDelay", true);
    connectionOptions.put("child.keepAlive", true);
    //TODO reuseAddress should be configurable
    connectionOptions.put("reuseAddress", true); //Not child since applies to the acceptor socket
  }

  /**
   * Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link NetSocket} and passes it to the
   * connect handler.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer connectHandler(EventHandler<NetSocket> connectHandler) {
    checkThread();
    this.connectHandler = connectHandler;
    return this;
  }

  /**
   * If {@code ssl} is {@code true}, this signifies the server will handle SSL connections
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetServer setSSL(boolean ssl) {
    checkThread();
    this.ssl = ssl;
    return this;
  }

  /**
   * Set the path to the SSL server key store. This method should only be used with the server in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL server key store is a standard Java Key Store, and should contain the server certificate. A key store
   * must be provided for SSL to be operational.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetServer setKeyStorePath(String path) {
    checkThread();
    this.keyStorePath = path;
    return this;
  }

  /**
   * Set the password for the SSL server key store. This method should only be used with the server in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetServer setKeyStorePassword(String pwd) {
    checkThread();
    this.keyStorePassword = pwd;
    return this;
  }

  /**
   * Set the path to the SSL server trust store. This method should only be used with the server in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * The SSL server trust store is a standard Java Key Store, and should contain the certificate(s) of the clients that the server trusts. The SSL
   * handshake will fail if the server requires client authentication and provides a certificate that the server does not trust.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetServer setTrustStorePath(String path) {
    checkThread();
    this.trustStorePath = path;
    return this;
  }

  /**
   * Set the password for the SSL server trust store. This method should only be used with the server in SSL mode, i.e. after {@link #setSSL(boolean)}
   * has been set to {@code true}.<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetServer setTrustStorePassword(String pwd) {
    checkThread();
    this.trustStorePassword = pwd;
    return this;
  }

  /**
   * Set {@code required} to true if you want the server to request client authentication from any connecting clients. This
   * is an extra level of security in SSL, and requires clients to provide client certificates. Those certificates must be added
   * to the server trust store.
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public NetServer setClientAuthRequired(boolean required) {
    checkThread();
    clientAuth = required ? ClientAuth.REQUIRED : ClientAuth.NONE;
    return this;
  }

  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections accepted by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer setTcpNoDelay(boolean tcpNoDelay) {
    checkThread();
    connectionOptions.put("child.tcpNoDelay", tcpNoDelay);
    return this;
  }

  /**
   * Set the TCP send buffer size for connections accepted by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer setSendBufferSize(int size) {
    checkThread();
    connectionOptions.put("child.sendBufferSize", size);
    return this;
  }

  /**
   * Set the TCP receive buffer size for connections accepted by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer setReceiveBufferSize(int size) {
    checkThread();
    connectionOptions.put("child.receiveBufferSize", size);
    return this;
  }

  /**
   * Set the TCP keepAlive setting for connections accepted by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer setKeepAlive(boolean keepAlive) {
    checkThread();
    connectionOptions.put("child.keepAlive", keepAlive);
    return this;
  }

  /**
   * Set the TCP reuseAddress setting for connections accepted by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer setReuseAddress(boolean reuse) {
    checkThread();
    connectionOptions.put("child.reuseAddress", reuse);
    return this;
  }

  /**
   * Set the TCP soLinger setting for connections accepted by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer setSoLinger(boolean linger) {
    checkThread();
    connectionOptions.put("child.soLinger", linger);
    return this;
  }

  /**
   * Set the TCP trafficClass setting for connections accepted by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer setTrafficClass(int trafficClass) {
    checkThread();
    connectionOptions.put("child.trafficClass", trafficClass);
    return this;
  }

  /**
   * Instruct the server to listen for incoming connections on the specified {@code port} and all available interfaces.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer listen(int port) {
    return listen(port, "0.0.0.0");
  }

  /**
   * Instruct the server to listen for incoming connections on the specified {@code port} and {@code host}. {@code host} can
   * be a host name or an IP address.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer listen(int port, String host) {
    checkThread();
    if (connectHandler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;

    serverChannelGroup = new DefaultChannelGroup("nodex-acceptor-channels");

    ChannelFactory factory =
        new NioServerSocketChannelFactory(
            NodexInternal.instance.getAcceptorPool(),
            NodexInternal.instance.getWorkerPool());
    ServerBootstrap bootstrap = new ServerBootstrap(factory);

    checkSSL();

    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();
        if (ssl) {
          SSLEngine engine = context.createSSLEngine();
          engine.setUseClientMode(false);
          switch (clientAuth) {
            case REQUEST: {
              engine.setWantClientAuth(true);
              break;
            }
            case REQUIRED: {
              engine.setNeedClientAuth(true);
              break;
            }
            case NONE: {
              engine.setNeedClientAuth(false);
              break;
            }
          }
          pipeline.addLast("ssl", new SslHandler(engine));
        }
        pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());  // For large file / sendfile support
        pipeline.addLast("handler", new ServerHandler());
        return pipeline;
      }
    });

    bootstrap.setOptions(connectionOptions);

    try {
      //TODO - currently bootstrap.bind is blocking - need to make it non blocking by not using bootstrap directly
      Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
      serverChannelGroup.add(serverChannel);
      System.out.println("Net server listening on " + host + ":" + port);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }

    return this;
  }

  /**
   * Close the server. This will close any currently open connections.
   */
  public void close() {
    close(null);
  }

  /**
   * Close the server. This will close any currently open connections. The event handler {@code done} will be called
   * when the close is complete.
   */
  public void close(final EventHandler<Void> done) {
    checkThread();

    long cid = Nodex.instance.getContextID();

    for (NetSocket sock : socketMap.values()) {
      sock.internalClose();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    NodexInternal.instance.setContextID(cid);

    if (done != null) {
      final Long contextID = Nodex.instance.getContextID();
      serverChannelGroup.close().addListener(new ChannelGroupFutureListener() {
        public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {

          Runnable runner = new Runnable() {
            public void run() {
              listening = false;
              done.onEvent(null);
            }
          };
          NodexInternal.instance.executeOnContext(contextID, runner);
        }
      });
    }
  }

  private void checkThread() {
    // All ops must always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread, actual: " + Thread.currentThread() + " expected: " + th);
    }
  }

  private class ServerHandler extends SimpleChannelHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final long contextID = NodexInternal.instance.associateContextWithWorker(ch.getWorker());
      ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
        public void run() {
          NodexInternal.instance.setContextID(contextID);
          NetSocket sock = new NetSocket(ch, contextID, Thread.currentThread());
          socketMap.put(ch, sock);
          connectHandler.onEvent(sock);
        }
      });
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
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.remove(ch);
      if (sock != null) {
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
          public void run() {
            sock.handleClosed();
            //System.out.println("Destroying context " + sock.getContextID());
            NodexInternal.instance.destroyContext(sock.getContextID());
          }
        });
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      NetSocket sock = socketMap.get(ch);
      ChannelBuffer buff = (ChannelBuffer) e.getMessage();
      sock.handleDataReceived(new Buffer(buff.slice()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      ch.close();
      final Throwable t = e.getCause();
      if (sock != null && t instanceof Exception) {
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
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
