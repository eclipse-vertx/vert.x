/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
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
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.AbstractServer;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;

import javax.net.ssl.SSLEngine;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultNetServer extends AbstractServer<NetSocket> implements NetServer {

  private static final Logger log = LoggerFactory.getLogger(DefaultNetServer.class);

  private final Map<Channel, DefaultNetSocket> socketMap = new ConcurrentHashMap();
  private Handler<NetSocket> connectHandler;
  private ChannelGroup serverChannelGroup;
  private boolean listening;
  private ServerID id;
  private DefaultNetServer actualServer;
  private final VertxWorkerPool availableWorkers = new VertxWorkerPool();
  private final HandlerManager<NetSocket> handlerManager = new HandlerManager<>(availableWorkers);

  public DefaultNetServer(VertxInternal vertx) {
	super(vertx);
	getTCPSSLHelper().setReuseAddress(true);
  }

  public NetServer connectHandler(Handler<NetSocket> connectHandler) {
    this.connectHandler = connectHandler;
    return this;
  }

  public final NetServer listen(int port) {
    return listen(port, "0.0.0.0");
  }
  
  public NetServer listen(int port, String host) {
    if (connectHandler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }
    listening = true;

    synchronized (getVertxInternal().sharedNetServers()) {
      id = new ServerID(port, host);
      DefaultNetServer shared = getVertxInternal().sharedNetServers().get(id);
      if (shared == null) {
        serverChannelGroup = new DefaultChannelGroup("vertx-acceptor-channels");

        ChannelFactory factory =
            new NioServerSocketChannelFactory(
            		getVertxInternal().getAcceptorPool(),
                availableWorkers);
        ServerBootstrap bootstrap = new ServerBootstrap(factory);

        getTCPSSLHelper().checkSSL();

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
          public ChannelPipeline getPipeline() {
            ChannelPipeline pipeline = Channels.pipeline();
            if (getTCPSSLHelper().isSSL()) {
              SSLEngine engine = getTCPSSLHelper().getSSLContext().createSSLEngine();
              engine.setUseClientMode(false);
              switch (getTCPSSLHelper().getClientAuth()) {
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

        bootstrap.setOptions(getTCPSSLHelper().generateConnectionOptions(true));

        try {
          //TODO - currently bootstrap.bind is blocking - need to make it non blocking by not using bootstrap directly
          Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName(host), port));
          serverChannelGroup.add(serverChannel);
          log.trace("Net server listening on " + host + ":" + port);
        } catch (UnknownHostException e) {
          log.error("Failed to bind", e);
        }
        getVertxInternal().sharedNetServers().put(id, this);
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        checkConfigs(actualServer, this);
        actualServer = shared;
      }
      actualServer.handlerManager.addHandler(connectHandler, getContext());
    }
    return this;
  }

  public void close() {
    close(null);
  }

  public void close(final Handler<Void> done) {
    if (!listening) {
      if (done != null) {
        executeCloseDone(getContext(), done);
      }
      return;
    }
    listening = false;
    synchronized (getVertxInternal().sharedNetServers()) {

      if (actualServer != null) {
        actualServer.handlerManager.removeHandler(connectHandler, getContext());

        if (actualServer.handlerManager.hasHandlers()) {
          // The actual server still has handlers so we don't actually close it
          if (done != null) {
            executeCloseDone(getContext(), done);
          }
        } else {
          // No Handlers left so close the actual server
          // The done handler needs to be executed on the context that calls close, NOT the context
          // of the actual server
          actualServer.actualClose(getContext(), done);
        }
      }
    }
  }

  private void actualClose(final Context closeContext, final Handler<Void> done) {
    if (id != null) {
      getVertxInternal().sharedNetServers().remove(id);
    }

    for (DefaultNetSocket sock : socketMap.values()) {
      sock.internalClose();
    }

    // We need to reset it since sock.internalClose() above can call into the close handlers of sockets on the same thread
    // which can cause context id for the thread to change!

    Context.setContext(closeContext);

    ChannelGroupFuture fut = serverChannelGroup.close();
    if (done != null) {
      fut.addListener(new ChannelGroupFutureListener() {
        public void operationComplete(ChannelGroupFuture channelGroupFuture) throws Exception {
          executeCloseDone(closeContext, done);
        }
      });
    }
  }

  private void checkConfigs(DefaultNetServer currentServer, DefaultNetServer newServer) {
    //TODO check configs are the same
  }

  private void executeCloseDone(final Context closeContext, final Handler<Void> done) {
    closeContext.execute(new Runnable() {
      public void run() {
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

      if (getTCPSSLHelper().isSSL()) {
        SslHandler sslHandler = (SslHandler)ch.getPipeline().get("ssl");

        ChannelFuture fut = sslHandler.handshake();
        fut.addListener(new ChannelFutureListener() {

          public void operationComplete(ChannelFuture channelFuture) throws Exception {
            if (channelFuture.isSuccess()) {
              connected(ch, handler);
            } else {
              log.error("Client from origin " + ch.getRemoteAddress() + " failed to connect over ssl");
            }
          }
        });

      } else {
        connected(ch, handler);
      }
    }

    private void connected(final NioSocketChannel ch, final HandlerHolder handler) {
      handler.context.execute(new Runnable() {
        public void run() {
          DefaultNetSocket sock = new DefaultNetSocket(getVertxInternal(), ch, handler.context);
          socketMap.put(ch, sock);
          handler.handler.handle(sock);
        }
      });
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final DefaultNetSocket sock = socketMap.get(ch);
      ChannelState state = e.getState();
      if (state == ChannelState.INTEREST_OPS) {
        sock.getContext().execute(new Runnable() {
          public void run() {
            sock.handleInterestedOpsChanged();
          }
        });
      }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final DefaultNetSocket sock = socketMap.remove(ch);
      if (sock != null) {
        sock.getContext().execute(new Runnable() {
          public void run() {
            sock.handleClosed();
          }
        });
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
      Channel ch = e.getChannel();
      DefaultNetSocket sock = socketMap.get(ch);
      if (sock != null) {
        ChannelBuffer buff = (ChannelBuffer) e.getMessage();
        sock.handleDataReceived(new Buffer(buff.slice()));
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final NetSocket sock = socketMap.remove(ch);
      ch.close();
      final Throwable t = e.getCause();

      log.error("Exception on netserver", t);

      if (sock != null && t instanceof Exception) {
        sock.getContext().execute(new Runnable() {
          public void run() {
            sock.handleException((Exception) t);
          }
        });
      } else {
        // Ignore - any exceptions not associated with any sock (e.g. failure in ssl handshake) will
        // be communicated explicitly
      }
    }
  }
}
