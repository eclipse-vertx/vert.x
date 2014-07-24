/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
import org.vertx.java.core.http.impl.ws.WebSocketFrameInternal;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.ConnectionBase;
import org.vertx.java.core.net.impl.DefaultNetSocket;
import org.vertx.java.core.net.impl.VertxNetHandler;

import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.*;

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
  private boolean keepAlive;
  private boolean pipelining;
  private boolean forcedClose;
  private boolean upgradedConnection;
  private WebSocketClientHandshaker handshaker;
  private volatile DefaultHttpClientRequest currentRequest;
  // Requests can be pipelined so we need a queue to keep track of requests
  private final Queue<DefaultHttpClientRequest> requests = new ArrayDeque<>();
  // the maximum number of pipelined requests. leave it "-1" to unlimit the number.
  private int maxOutstandingRequest = -1;
  private volatile DefaultHttpClientResponse currentResponse;
  private DefaultWebSocket ws;

  ClientConnection(VertxInternal vertx, DefaultHttpClient client, Channel channel, boolean ssl, String host,
                   int port,
                   boolean keepAlive,
                   boolean pipelining,
                   DefaultContext context) {
    super(vertx, channel, context);
    this.client = client;
    this.ssl = ssl;
    this.host = host;
    this.port = port;
    if ((port == 80 && !ssl) || (port == 443 && ssl)) {
      this.hostHeader = host;
    } else {
      this.hostHeader = host + ':' + port;
    }
    this.keepAlive = keepAlive;
    this.pipelining = pipelining;
  }


  void toWebSocket(String uri,
                   final WebSocketVersion wsVersion,
                   final MultiMap headers,
                   int maxWebSocketFrameSize,
                   final Set<String> subProtocols,
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
      String wsSubProtocols = null;
      if (subProtocols != null && !subProtocols.isEmpty()) {
        StringBuilder sb = new StringBuilder();

        Iterator<String> protocols = subProtocols.iterator();
        while (protocols.hasNext()) {
          sb.append(protocols.next());
          if (protocols.hasNext()) {
            sb.append(",");
          }
        }
        wsSubProtocols = sb.toString();
      }
      handshaker = WebSocketClientHandshakerFactory.newHandshaker(wsuri, version, wsSubProtocols, false,
                                                                  nettyHeaders, maxWebSocketFrameSize);
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
      upgradedConnection = true;

    } catch (Exception e) {
      handleException(e);
    }
  }

  private final class HandshakeInboundHandler extends ChannelInboundHandlerAdapter {
    private final Handler<WebSocket> wsConnect;
    private final DefaultContext context;
    private FullHttpResponse response;
    private boolean handshaking = true;
    private final Queue<Object> buffered = new ArrayDeque<>();
    public HandshakeInboundHandler(final Handler<WebSocket> wsConnect) {
      this.wsConnect = wsConnect;
      this.context = vertx.getContext();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      super.channelInactive(ctx);
      context.execute(ctx.channel().eventLoop(), new Runnable() {
        @Override
        public void run() {
          // if still handshaking this means we not got any response back from the server and so need to notify the client
          // about it as otherwise the client would never been notified.
          if (handshaking) {
            handleException(new WebSocketHandshakeException("Connection closed while handshake in process"));
          }
        }
      });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      context.execute(ctx.channel().eventLoop(), new Runnable() {
        public void run() {
          if (handshaker != null && handshaking) {
            if (msg instanceof HttpResponse) {
              HttpResponse resp = (HttpResponse) msg;
              if (resp.getStatus().code() != 101) {
                handleException(new WebSocketHandshakeException("Websocket connection attempt returned HTTP status code " + resp.getStatus().code()));
                return;
              }
              response = new DefaultFullHttpResponse(resp.getProtocolVersion(), resp.getStatus());
              response.headers().add(resp.headers());
            }

            if (msg instanceof HttpContent) {
              if (response != null) {
                response.content().writeBytes(((HttpContent) msg).content());
                if (msg instanceof LastHttpContent) {
                  response.trailingHeaders().add(((LastHttpContent) msg).trailingHeaders());
                  try {
                    handshakeComplete(ctx, response);
                    channel.pipeline().remove(HandshakeInboundHandler.this);
                    for (;;) {
                      Object m = buffered.poll();
                      if (m == null) {
                        break;
                      }
                      ctx.fireChannelRead(m);
                    }
                  } catch (WebSocketHandshakeException e) {
                    actualClose();
                    handleException(e);
                  }
                }
              }
            }
          } else {
            buffered.add(msg);
          }
        }
      });
    }

    private void handleException(WebSocketHandshakeException e) {
      handshaking = false;
      buffered.clear();
      client.handleException(e);
    }

    private void handshakeComplete(ChannelHandlerContext ctx, FullHttpResponse response) {
      handshaking = false;
      ChannelHandler handler = ctx.pipeline().get(HttpContentDecompressor.class);
      if (handler != null) {
        // remove decompressor as its not needed anymore once connection was upgraded to websockets
        ctx.pipeline().remove(handler);
      }
      ws = new DefaultWebSocket(vertx, ClientConnection.this);
      handshaker.finishHandshake(channel, response);
      log.debug("WebSocket handshake complete");
      wsConnect.handle(ws);
    }
  }

  public void closeHandler(final Handler<Void> handler) {
    this.closeHandler = new Handler<Void>() {
        @Override
        public void handle(Void event) {
            if(getOutstandingRequestCount() > 0) {
                // As soon as a response is handled for the request it's no longer in the requests queue so we should
                // only be informing requests that haven't received a response.
                for (DefaultHttpClientRequest request : requests) {
                    // If the request has already received a timeout or other exception then don't inform it that the
                    // connection was closed.
                    if (!request.hasExceptionOccurred()) {
                        request.handleException(new ClosedChannelException());
                    }
                }
            }

            handler.handle(event);
        }
    };
  }

  @Override
  public void close() {
    if (upgradedConnection) {
      // Close it
      actualClose();
    } else if (!keepAlive || forcedClose) {
      // Close it
      actualClose();
    } else {
      // Keep alive
      client.returnConnection(this);
    }
  }

  void actualClose() {
    super.close();
  }

  boolean isClosed() {
    return !channel.isOpen();
  }

  int getOutstandingRequestCount() {
    return requests.size();
  }

  public void setMaxOutstandingRequestCount(final int maxOutstandingRequestCount) {
    maxOutstandingRequest = maxOutstandingRequestCount;
  }

  public boolean isFullyOccupied() {
    return maxOutstandingRequest != -1 && requests.size() >= maxOutstandingRequest;
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
      forcedClose = isConnectionCloseHeader(currentResponse);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
    if (!keepAlive || !pipelining || forcedClose) {
      // If keepAlive is true and pipelining is true, then the connection was returned to the
      // pool in endRequest
      close();
    }
  }

  void handleWsFrame(WebSocketFrameInternal frame) {
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

    // If keepAlive is true and pipelining is true, then return the connection to the
    // pool so that other requests can use the same HTTP connection without waiting for
    // the response of the current request (HTTP pipelining). Otherwise the connection
    // is closed (if keepAlive is false) or returned to the pool (if keepAlive is true)
    // after receiving the response
    if (keepAlive && pipelining) {
      //Close just returns connection to the pool
      close();
    }
  }

  NetSocket createNetSocket() {
    // connection was upgraded to raw TCP socket
    upgradedConnection = true;
    DefaultNetSocket socket = new DefaultNetSocket(vertx, channel, context, client.tcpHelper, true);
    Map<Channel, DefaultNetSocket> connectionMap = new HashMap<Channel, DefaultNetSocket>(1);
    connectionMap.put(channel, socket);

    // Flush out all pending data
    endReadAndFlush();


    // remove old http handlers and replace the old handler with one that handle plain sockets
    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler inflater = pipeline.get(HttpContentDecompressor.class);
    if (inflater != null) {
      pipeline.remove(inflater);
    }
    pipeline.remove("codec");
    pipeline.replace("handler", "handler", new VertxNetHandler(client.vertx, connectionMap) {
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

  private boolean isConnectionCloseHeader(DefaultHttpClientResponse response) {
    if (response != null) {
      List<String> connectionHeaderValues = response.headers().getAll(HttpHeaders.Names.CONNECTION);
      if (connectionHeaderValues != null) {
        for (String connectionHeaderValue : connectionHeaderValues) {
          if (HttpHeaders.Values.CLOSE.equals(connectionHeaderValue)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
