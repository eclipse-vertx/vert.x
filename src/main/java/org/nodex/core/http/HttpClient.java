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

package org.nodex.core.http;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioSocketChannel;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.nodex.core.NodexInternal;
import org.nodex.core.ThreadSourceUtils;
import org.nodex.core.buffer.Buffer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpClient {

  private final ClientBootstrap bootstrap;
  private Map<Channel, HttpClientConnection> connectionMap = new ConcurrentHashMap();
  private boolean keepAlive;

  public HttpClient() {
    bootstrap = new ClientBootstrap(
        new NioClientSocketChannelFactory(
            NodexInternal.instance.getAcceptorPool(),
            NodexInternal.instance.getWorkerPool()));

    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        // SSL TODO
//          if (ssl) {
//              SSLEngine engine =
//                  SecureChatSslContextFactory.getClientContext().createSSLEngine();
//              engine.setUseClientMode(true);
//
//              pipeline.addLast("ssl", new SslHandler(engine));
//          }
        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("decoder", new HttpResponseDecoder());
        pipeline.addLast("handler", new ClientHandler());
        return pipeline;
      }
    });
  }

  public HttpClient setKeepAlive(boolean keepAlive) {
    this.keepAlive = keepAlive;
    return this;
  }

  public HttpClient connect(final HttpClientConnectHandler connectHandler) {
    return connect(80, "localhost", connectHandler);
  }

  public HttpClient connect(String host, final HttpClientConnectHandler connectHandler) {
    return connect(80, host, connectHandler);
  }

  public HttpClient connect(final int port, final String host, final HttpClientConnectHandler connectHandler) {
    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));

    future.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture channelFuture) throws Exception {
        if (channelFuture.isSuccess()) {
          final NioSocketChannel ch = (NioSocketChannel) channelFuture.getChannel();
          final String contextID = NodexInternal.instance.createContext(ch.getWorker());
          ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
            public void run() {
              HttpClientConnection conn = new HttpClientConnection(ch, keepAlive, host + ":" + port, contextID,
                  Thread.currentThread());
              connectionMap.put(ch, conn);
              NodexInternal.instance.setContextID(contextID);
              connectHandler.onConnect(conn);
            }
          });
        } else {
          //FIXME - better error handling
          Throwable t = channelFuture.getCause();
          if (t != null) {
            t.printStackTrace(System.err);
          }
        }
      }
    });

    return this;
  }

  public void close() {
    for (HttpClientConnection conn : connectionMap.values()) {
      conn.close();
    }
  }

  private class ClientHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final HttpClientConnection conn = connectionMap.remove(ch);
      if (conn != null) {
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
          public void run() {
            conn.handleClosed();
            NodexInternal.instance.destroyContext(conn.getContextID());
          }
        });
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final HttpClientConnection conn = connectionMap.get(ch);
      ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
        public void run() {
          conn.handleInterestedOpsChanged();
        }
      });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
      final NioSocketChannel ch = (NioSocketChannel) e.getChannel();
      final HttpClientConnection conn = connectionMap.get(ch);
      final Throwable t = e.getCause();
      if (conn != null && t instanceof Exception) {
        ThreadSourceUtils.runOnCorrectThread(ch, new Runnable() {
          public void run() {
            conn.handleException((Exception) t);
          }
        });
      } else {
        t.printStackTrace(System.err);
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      Channel ch = e.getChannel();
      HttpClientConnection conn = connectionMap.get(ch);
      Object msg = e.getMessage();
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) msg;
        conn.handleResponse(new HttpClientResponse(conn, response));
        ChannelBuffer content = response.getContent();

        if (content.readable()) {
          conn.handleChunk(new Buffer(content));
        }
        if (!response.isChunked()) {
          conn.handleEnd();
        }
      } else if (msg instanceof HttpChunk) {
        HttpChunk chunk = (HttpChunk) msg;
        Buffer buff = new Buffer(chunk.getContent());
        conn.handleChunk(buff);
        if (chunk.isLast()) {
          if (chunk instanceof HttpChunkTrailer) {
            HttpChunkTrailer trailer = (HttpChunkTrailer) chunk;
            conn.handleEnd(trailer);
          } else {
            conn.handleEnd();
          }
        }
      } else if (msg instanceof WebSocketFrame) {
        WebSocketFrame frame = (WebSocketFrame) msg;
        conn.handleWsFrame(frame);
      } else {
        throw new IllegalStateException("Invalid object " + e.getMessage());
      }
    }
  }
}
