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

package org.nodex.java.core.net;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.logging.Logger;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>NetClient is an asynchronous factory for TCP or SSL connections</p>
 *
 * <p>Multiple connections to different servers can be made using the same instance. Instances of this class can be shared by different
 * event loops.</p>
 *
 * <p>This client supports a configurable number of connection attempts and a configurable delay between attempts.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClient extends NetClientBase {

  private static final Logger log = Logger.getLogger(NetClient.class);

  private ClientBootstrap bootstrap;
  private NioClientSocketChannelFactory channelFactory;
  private Map<Channel, NetSocket> socketMap = new ConcurrentHashMap<>();
  private Handler<Exception> exceptionHandler;
  private int reconnectAttempts;
  private long reconnectInterval = 1000;

  /**
   * Create a new {@code NetClient}
   */
  public NetClient() {
    super();
  }

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP addresss. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  public NetClient connect(int port, String host, final Handler<NetSocket> connectHandler) {
    return connect(port, host, connectHandler, reconnectAttempts);
  }

  private NetClient connect(final int port, final String host, final Handler<NetSocket> connectHandler, final int remainingAttempts) {

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
        final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();

        if (channelFuture.isSuccess()) {
          runOnCorrectThread(ch, new Runnable() {
            public void run() {
              NodexInternal.instance.setContextID(contextID);
              NetSocket sock = new NetSocket(ch, contextID, Thread.currentThread());
              socketMap.put(ch, sock);
              connectHandler.handle(sock);
            }
          });
        } else {
          if (remainingAttempts > 0) {
            runOnCorrectThread(ch, new Runnable() {
              public void run() {
                NodexInternal.instance.setContextID(contextID);
                log.debug("Failed to create connection. Will retry in " + reconnectInterval + " milliseconds");
                //Set a timer to retry connection
                Nodex.instance.setTimer(reconnectInterval, new Handler<Long>() {
                  public void handle(Long timerID) {
                    connect(port, host, connectHandler, remainingAttempts - 1);
                  }
                });
               }
            });
          } else {
            Throwable t = channelFuture.getCause();
            if (t instanceof Exception && exceptionHandler != null) {
              exceptionHandler.handle((Exception) t);
            } else {
              log.error(t);
            }
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
  public NetClient connect(int port, Handler<NetSocket> connectCallback) {
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
   * Set the number of reconnection attempts. In the event a connection attempt fails, the client will attempt
   * to connect a further number of times, before it fails. Default value is zero.
   */
  public void setReconnectAttempts(int attempts) {
    if (attempts < 0) {
      throw new IllegalArgumentException("Invalid attempts: " + attempts);
    }
    this.reconnectAttempts = attempts;
  }

  /**
   * Get the number of reconnect attempts
   */
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  /**
   * Set the reconnect interval, in milliseconds
   */
  public void setReconnectInterval(long interval) {
    if (interval < 1) {
      throw new IllegalArgumentException("Invalid interval: " + interval);
    }
    this.reconnectInterval = interval;
  }

  /**
   * Get the reconnect interval, in milliseconds.
   */
  public long getReconnectInterval() {
    return reconnectInterval;
  }

  /**
   * Set the exception handler. Any exceptions that occur during connect or later on will be notified via the {@code handler}.
   * If no handler is supplied any exceptions will be printed to {@link System#err}
   */
  public void exceptionHandler(Handler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setSSL(boolean ssl) {
    return (NetClient)super.setSSL(ssl);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setKeyStorePath(String path) {
    return (NetClient)super.setKeyStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setKeyStorePassword(String pwd) {
    return (NetClient)super.setKeyStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setTrustStorePath(String path) {
    return (NetClient)super.setTrustStorePath(path);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setTrustStorePassword(String pwd) {
    return (NetClient)super.setTrustStorePassword(pwd);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setTrustAll(boolean trustAll) {
    return (NetClient)super.setTrustAll(trustAll);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setTcpNoDelay(boolean tcpNoDelay) {
    return (NetClient)super.setTcpNoDelay(tcpNoDelay);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setSendBufferSize(int size) {
    return (NetClient)super.setSendBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setReceiveBufferSize(int size) {
    return (NetClient)super.setReceiveBufferSize(size);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setTCPKeepAlive(boolean keepAlive) {
    return (NetClient)super.setTCPKeepAlive(keepAlive);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setReuseAddress(boolean reuse) {
    return (NetClient)super.setReuseAddress(reuse);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setSoLinger(boolean linger) {
    return (NetClient)super.setSoLinger(linger);
  }

  /**
   * {@inheritDoc}
   */
  public NetClient setTrafficClass(int trafficClass) {
    return (NetClient)super.setTrafficClass(trafficClass);
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
        runOnCorrectThread(ch, new Runnable() {
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
