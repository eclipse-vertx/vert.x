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

package org.vertx.java.core.http.impl;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.ReferenceCountUtil;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketVersion;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.ConnectionBase;
import org.vertx.java.core.net.impl.DefaultNetSocket;
import org.vertx.java.core.net.impl.VertxNetHandler;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ClientConnection extends ConnectionBase {
  private static final Logger log = LoggerFactory.getLogger(ClientConnection.class);

  final DefaultHttpClient client;
  final String hostHeader;
  private final boolean ssl;
  private final String host;
  private final int port;
  boolean keepAlive;
  private boolean wsHandshakeConnection;
  private WebSocketClientHandshaker handshaker;
  private volatile DefaultHttpClientRequest currentRequest;
  // Requests can be pipelined so we need a queue to keep track of requests
  private final Queue<DefaultHttpClientRequest> requests = new ArrayDeque<>();
  private volatile DefaultHttpClientResponse currentResponse;
  private DefaultWebSocket ws;

  ClientConnection(VertxInternal vertx, DefaultHttpClient client, Channel channel, boolean ssl, String host,
                   int port,
                   boolean keepAlive,
                   DefaultContext context) {
    super(vertx, channel, context);
    this.client = client;
    this.ssl = ssl;
    this.host = host;
    this.port = port;
    this.hostHeader = host + ':' + port;
    this.keepAlive = keepAlive;
  }


  void toWebSocket(String uri,
                   final WebSocketVersion wsVersion,
                   final MultiMap headers,
                   final Handler<WebSocket> wsConnect) {
    if (ws != null) {
      throw new IllegalStateException("Already websocket");
    }

    try {
      URI wsuri = new URI(uri);
      if (!wsuri.isAbsolute()) {
        // Netty requires an absolute url
        wsuri = new URI((ssl ? "https:" : "http:") + "//" + host + ":" + port + uri);
      }
      io.netty.handler.codec.http.websocketx.WebSocketVersion version;
      if (wsVersion == WebSocketVersion.HYBI_00) {
        version = io.netty.handler.codec.http.websocketx.WebSocketVersion.V00;
      } else if (wsVersion == WebSocketVersion.HYBI_08) {
        version = io.netty.handler.codec.http.websocketx.WebSocketVersion.V08;
      } else if (wsVersion == WebSocketVersion.RFC6455) {
        version = io.netty.handler.codec.http.websocketx.WebSocketVersion.V13;
      } else {
        throw new IllegalArgumentException("Invalid version");
      }
      HttpHeaders nettyHeaders;
      if (headers != null) {
        nettyHeaders = new DefaultHttpHeaders();
        for (Map.Entry<String, String> entry: headers) {
          nettyHeaders.add(entry.getKey(), entry.getValue());
        }
      } else {
        nettyHeaders = null;
      }
      handshaker = WebSocketClientHandshakerFactory.newHandshaker(wsuri, version, null, false, nettyHeaders);
      final ChannelPipeline p = channel.pipeline();
      p.addBefore("handler", "handshakeCompleter", new HandshakeInboundHandler(wsConnect));

      handshaker.handshake(channel).addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          if (!future.isSuccess()) {
            client.handleException((Exception) future.cause());
          }
        }
      });
      wsHandshakeConnection = true;

    } catch (Exception e) {
      handleException(e);
    }
  }

  private final class HandshakeInboundHandler extends ChannelInboundHandlerAdapter {
    private final Handler<WebSocket> wsConnect;
    private final DefaultContext context;
    private FullHttpResponse response;
    private boolean handshaking;
    private Queue<Object> buffered = new ArrayDeque<>();
    public HandshakeInboundHandler(final Handler<WebSocket> wsConnect) {
      this.wsConnect = wsConnect;
      this.context = vertx.getContext();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      context.execute(ctx.channel().eventLoop(), new Runnable() {
        public void run() {
          boolean fire = false;
          try {

            if (handshaker != null && !handshaking) {
              if (msg instanceof HttpResponse) {
                HttpResponse resp = (HttpResponse) msg;
                if (resp.getStatus().code() != 101) {
                  throw new WebSocketHandshakeException("Websocket connection attempt returned HTTP status code " + resp.getStatus().code());
                }
                response = new DefaultFullHttpResponse(resp.getProtocolVersion(), resp.getStatus());
                response.headers().add(resp.headers());
              }

              if (msg instanceof HttpContent) {
                if (response != null) {
                  response.content().writeBytes(((HttpContent) msg).content());
                  if (msg instanceof LastHttpContent) {
                    response.trailingHeaders().add(((LastHttpContent) msg).trailingHeaders());
                    // copy over messages that are not processed yet
                    handshakeComplete(ctx, response);
                    channel.pipeline().remove(HandshakeInboundHandler.this);

                    fire = true;
                  }
                }
              }
            } else {
              buffered.add(msg);
            }
          } catch (WebSocketHandshakeException e) {
            fire = false;
            for (;;) {
              Object m = buffered.poll();
              if (m == null) {
                break;
              }
            }
            client.handleException(e);
          } finally {
            if (fire) {
              for (;;) {
                Object m = buffered.poll();
                if (m == null) {
                  break;
                }
                ctx.fireChannelRead(m);
              }
            }
          }
        }
      });

    }

    private void handshakeComplete(ChannelHandlerContext ctx, FullHttpResponse response) {
      handshaking = true;
      try {
        //ctx.pipeline().addAfter(ctx.name(), "websocketConverter", WebSocketConvertHandler.INSTANCE);
        ws = new DefaultWebSocket(vertx, ClientConnection.this);
        handshaker.finishHandshake(channel, response);
        log.debug("WebSocket handshake complete");
        wsConnect.handle(ws);
      } catch (WebSocketHandshakeException e) {
        client.handleException(e);
      }
    }
  }

  public void closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
  }

  @Override
  public void close() {
    if (wsHandshakeConnection) {
      // Do nothing - this will be ugraded
    } else if (!keepAlive) {
      //Close it
      super.close();
    } else {
      client.returnConnection(this);
    }
  }

  boolean isClosed() {
    return !channel.isOpen();
  }

  int getOutstandingRequestCount() {
    return requests.size();
  }

  //TODO - combine these with same in ServerConnection and NetSocket
  @Override
  public void handleInterestedOpsChanged() {
    try {
      if (!doWriteQueueFull()) {
        if (currentRequest != null) {
          setContext();
          currentRequest.handleDrained();
        } else if (ws != null) {
          ws.writable();
        }
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }


  void handleResponse(HttpResponse resp) {
    DefaultHttpClientRequest req;
    if (resp.getStatus().code() == 100) {
      //If we get a 100 continue it will be followed by the real response later, so we don't remove it yet
      req = requests.peek();
    } else {
      req = requests.poll();
    }
    if (req == null) {
      throw new IllegalStateException("No response handler");
    }
    setContext();
    DefaultHttpClientResponse nResp = new DefaultHttpClientResponse(vertx, req, this, resp);
    currentResponse = nResp;
    req.handleResponse(nResp);
  }

  void handleResponseChunk(Buffer buff) {
    setContext();
    try {
      currentResponse.handleChunk(buff);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleResponseEnd(LastHttpContent trailer) {
    setContext();
    try {
      currentResponse.handleEnd(trailer);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
    if (!keepAlive) {
      close();
    }
  }

  void handleWsFrame(WebSocketFrame frame) {
    if (ws != null) {
      setContext();
      ws.handleFrame(frame);
    }
  }

  protected void handleClosed() {
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }
  }

  protected DefaultContext getContext() {
    return super.getContext();
  }

  @Override
  protected void handleException(Throwable e) {
    super.handleException(e);
    if (currentRequest != null) {
      currentRequest.handleException(e);
    } else if (currentResponse != null) {
      currentResponse.handleException(e);
    }
  }

  void setCurrentRequest(DefaultHttpClientRequest req) {
    if (currentRequest != null) {
      throw new IllegalStateException("Connection is already writing a request");
    }
    this.currentRequest = req;
    this.requests.add(req);
  }

  void endRequest() {
    if (currentRequest == null) {
      throw new IllegalStateException("No write in progress");
    }
    currentRequest = null;

    if (keepAlive) {
      //Close just returns connection to the pool
      close();
    } else {
      //The connection gets closed after the response is received
    }
  }

  NetSocket createNetSocket() {
    DefaultNetSocket socket = new DefaultNetSocket(vertx, channel, context);
    Map<Channel, DefaultNetSocket> connectionMap = new HashMap<Channel, DefaultNetSocket>(1);
    connectionMap.put(channel, socket);

    // Flush out all pending data
    endReadAndFlush();

    // remove old http handlers and replace the old handler with one that handle plain sockets
    channel.pipeline().remove("codec");
    channel.pipeline().replace("handler", "handler", new VertxNetHandler(client.vertx, connectionMap) {
      @Override
      public void exceptionCaught(ChannelHandlerContext chctx, Throwable t) throws Exception {
        // remove from the real mapping
        client.connectionMap.remove(channel);
        super.exceptionCaught(chctx, t);
      }

      @Override
      public void channelInactive(ChannelHandlerContext chctx) throws Exception {
        // remove from the real mapping
        client.connectionMap.remove(channel);
        super.channelInactive(chctx);
      }

      @Override
      public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
        if (msg instanceof HttpContent) {
          ReferenceCountUtil.release(msg);
          return;
        }
        super.channelRead(chctx, msg);
      }
    });
    return socket;
  }
}
