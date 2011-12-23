/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net;

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
import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.mozilla.javascript.Context;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

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
 * {@link org.vertx.java.core.Handler} which is supplied to the server via the {@link #connectHandler} method.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetServer extends NetServerBase {

  private static final Logger log = Logger.getLogger(NetServer.class);

  //For debug only
  public static int numServers() {
    return servers.size();
  }

  private static final Map<ServerID, NetServer> servers = new HashMap<>();

  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap();
  private Handler<NetSocket> connectHandler;
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private ServerID id;
  private NetServer actualServer;
  private NetServerWorkerPool availableWorkers = new NetServerWorkerPool();
  private HandlerManager<NetSocket> handlerManager = new HandlerManager<>(availableWorkers);


  /**
   * Create a new NetServer instance.
   */
  public NetServer() {
    super();
  }

  /**
   * Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link NetSocket} and passes it to the
   * connect handler.
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetServer connectHandler(Handler<NetSocket> connectHandler) {
    checkThread();
    this.connectHandler = connectHandler;
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setSSL(boolean ssl) {
    checkThread();
    return (NetServer)super.setSSL(ssl);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setKeyStorePath(String path) {
    checkThread();
    return (NetServer)super.setKeyStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setKeyStorePassword(String pwd) {
    checkThread();
    return (NetServer)super.setKeyStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setTrustStorePath(String path) {
    checkThread();
    return (NetServer)super.setTrustStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setTrustStorePassword(String pwd) {
    checkThread();
    return (NetServer)super.setTrustStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setClientAuthRequired(boolean required) {
    checkThread();
    return (NetServer)super.setClientAuthRequired(required);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setTcpNoDelay(boolean tcpNoDelay) {
    checkThread();
    return (NetServer)super.setTcpNoDelay(tcpNoDelay);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setSendBufferSize(int size) {
    checkThread();
    return (NetServer)super.setSendBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setReceiveBufferSize(int size) {
    checkThread();
    return (NetServer)super.setReceiveBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setTCPKeepAlive(boolean keepAlive) {
    checkThread();
    return (NetServer)super.setTCPKeepAlive(keepAlive);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setReuseAddress(boolean reuse) {
    checkThread();
    return (NetServer)super.setReuseAddress(reuse);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setSoLinger(boolean linger) {
    checkThread();
    return (NetServer)super.setSoLinger(linger);
  }

  /**
   * {@inheritDoc}
   */
  public NetServer setTrafficClass(int trafficClass) {
    checkThread();
    return (NetServer)super.setTrafficClass(trafficClass);
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

    log.info("Java, in listen: " + port + " " + host);

    checkThread();
    if (connectHandler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;

    synchronized (servers) {
      id = new ServerID(port, host);
      NetServer shared = servers.get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels");

        ChannelFactory factory =
            new NioServerSocketChannelFactory(
                VertxInternal.instance.getAcceptorPool(),
                availableWorkers);
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
          log.trace("Net server listening on " + host + ":" + port);
        } catch (UnknownHostException e) {
          e.printStackTrace();
        }
        servers.put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        checkConfigs(actualServer, this);
        actualServer = shared;
      }
      actualServer.handlerManager.addHandler(connectHandler);
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
  public void close(final Handler<Void> done) {
    checkThread();

    if (!listening) return;
    listening = false;

    synchronized (servers) {

      actualServer.handlerManager.removeHandler(connectHandler);

      if (actualServer.handlerManager.hasHandlers()) {
        // The actual server still has handlers so we don't actually close it
        if (done != null) {
          executeCloseDone(contextID, done);
        }
      } else {
        // No Handlers left so close the actual server
        // The done handler needs to be executed on the context that calls close, NOT the context
        // of the actual server
        actualServer.actualClose(contextID, done);
      }
    }
  }

  private void actualClose(final long closeContextID, final Handler<Void> done) {
    if (id != null) {
      servers.remove(id);
    }

    for (NetSocket sock : socketMap.values()) {
      sock.internalClose();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    VertxInternal.instance.setContextID(closeContextID);

    ChannelGroupFuture fut = serverChannelGroup.close();
    if (done != null) {
      fut.addListener(new ChannelGroupFutureListener() {
        public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
          executeCloseDone(closeContextID, done);
        }
      });
    }
  }

  private void checkConfigs(NetServer currentServer, NetServer newServer) {
    //TODO check configs are the same
  }

  private void executeCloseDone(final long closeContextID, final Handler<Void> done) {
    VertxInternal.instance.executeOnContext(closeContextID, new Runnable() {
      public void run() {
        VertxInternal.instance.setContextID(closeContextID);
        done.handle(null);
      }
    });
  }

  private class ServerHandler extends SimpleChannelHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {

      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      NioWorker worker = ch.getWorker();

      //Choose a handler
      final HandlerHolder handler = handlerManager.chooseHandler(worker);

      if (handler == null) {
        //Ignore
        return;
      }

      VertxInternal.instance.executeOnContext(handler.contextID, new Runnable() {
        public void run() {
          VertxInternal.instance.setContextID(handler.contextID);
          NetSocket sock = new NetSocket(ch, handler.contextID, Thread.currentThread());
          socketMap.put(ch, sock);


          log.info("creating new netsocket, context is " + Context.getCurrentContext());

          handler.handler.handle(sock);
        }
      });
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      ChannelState state = e.getState();
      if (state == ChannelState.INTEREST_OPS) {
        VertxInternal.instance.executeOnContext(sock.getContextID(), new Runnable() {
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
        VertxInternal.instance.executeOnContext(sock.getContextID(), new Runnable() {
          public void run() {
            sock.handleClosed();
          }
        });
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      NetSocket sock = socketMap.get(ch);
      if (sock != null) {
        ChannelBuffer buff = (ChannelBuffer) e.getMessage();
        sock.handleDataReceived(new Buffer(buff.slice()));
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.get(ch);
      ch.close();
      final Throwable t = e.getCause();
      if (sock != null && t instanceof Exception) {
        VertxInternal.instance.executeOnContext(sock.getContextID(), new Runnable() {
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
