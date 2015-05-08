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

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebsocketVersion;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxNetHandler;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ClientConnection extends ConnectionBase {

  private static final Logger log = LoggerFactory.getLogger(ClientConnection.class);

  private final HttpClientImpl client;
  private final String hostHeader;
  private final boolean ssl;
  private final String host;
  private final int port;
  private final ConnectionLifeCycleListener listener;
  // Requests can be pipelined so we need a queue to keep track of requests
  private final Queue<HttpClientRequestImpl> requests = new ArrayDeque<>();
  private final Handler<Throwable> exceptionHandler;
  private final Object metric;
  private final HttpClientMetrics metrics;

  private WebSocketClientHandshaker handshaker;
  private HttpClientRequestImpl currentRequest;
  private HttpClientResponseImpl currentResponse;
  private HttpClientRequestImpl requestForResponse;
  private WebSocketImpl ws;

  ClientConnection(VertxInternal vertx, HttpClientImpl client, Handler<Throwable> exceptionHandler, Channel channel, boolean ssl, String host,
                   int port, ContextImpl context, ConnectionLifeCycleListener listener, HttpClientMetrics metrics) {
    super(vertx, channel, context, metrics);
    this.client = client;
    this.ssl = ssl;
    this.host = host;
    this.port = port;
    if ((port == 80 && !ssl) || (port == 443 && ssl)) {
      this.hostHeader = host;
    } else {
      this.hostHeader = host + ':' + port;
    }
    this.listener = listener;
    this.exceptionHandler = exceptionHandler;
    this.metrics = metrics;
    this.metric = metrics.connected(remoteAddress());
  }

  @Override
  protected Object metric() {
    return metric;
  }

  protected HttpClientMetrics metrics() {
    return metrics;
  }

  synchronized void toWebSocket(String requestURI, MultiMap headers, WebsocketVersion vers, String subProtocols,
                   int maxWebSocketFrameSize, Handler<WebSocket> wsConnect) {
    if (ws != null) {
      throw new IllegalStateException("Already websocket");
    }

    try {
      URI wsuri = new URI(requestURI);
      if (!wsuri.isAbsolute()) {
        // Netty requires an absolute url
        wsuri = new URI((ssl ? "https:" : "http:") + "//" + host + ":" + port + requestURI);
      }
      WebSocketVersion version =
         WebSocketVersion.valueOf((vers == null ?
           WebSocketVersion.V13 : vers).toString());
      HttpHeaders nettyHeaders;
      if (headers != null) {
        nettyHeaders = new DefaultHttpHeaders();
        for (Map.Entry<String, String> entry: headers) {
          nettyHeaders.add(entry.getKey(), entry.getValue());
        }
      } else {
        nettyHeaders = null;
      }
      handshaker = WebSocketClientHandshakerFactory.newHandshaker(wsuri, version, subProtocols, false,
                                                                  nettyHeaders, maxWebSocketFrameSize);
      ChannelPipeline p = channel.pipeline();
      p.addBefore("handler", "handshakeCompleter", new HandshakeInboundHandler(wsConnect, version != WebSocketVersion.V00));
      handshaker.handshake(channel).addListener(future -> {
        if (!future.isSuccess() && exceptionHandler != null) {
          exceptionHandler.handle(future.cause());
        }
      });
    } catch (Exception e) {
      handleException(e);
    }
  }

  private final class HandshakeInboundHandler extends ChannelInboundHandlerAdapter {

    private final boolean supportsContinuation;
    private final Handler<WebSocket> wsConnect;
    private final ContextImpl context;
    private final Queue<Object> buffered = new ArrayDeque<>();
    private FullHttpResponse response;
    private boolean handshaking = true;

    public HandshakeInboundHandler(Handler<WebSocket> wsConnect, boolean supportsContinuation) {
      this.supportsContinuation = supportsContinuation;
      this.wsConnect = wsConnect;
      this.context = vertx.getContext();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      super.channelInactive(ctx);
      context.executeFromIO(() -> {
        // if still handshaking this means we not got any response back from the server and so need to notify the client
        // about it as otherwise the client would never been notified.
        if (handshaking) {
          handleException(new WebSocketHandshakeException("Connection closed while handshake in process"));
        }
      });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      context.executeFromIO(() -> {
        synchronized (ClientConnection.this) {
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
                    for (; ; ) {
                      Object m = buffered.poll();
                      if (m == null) {
                        break;
                      }
                      ctx.fireChannelRead(m);
                    }
                  } catch (WebSocketHandshakeException e) {
                    close();
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
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
      }
    }

    private void handshakeComplete(ChannelHandlerContext ctx, FullHttpResponse response) {
      handshaking = false;
      ChannelHandler handler = ctx.pipeline().get(HttpContentDecompressor.class);
      if (handler != null) {
        // remove decompressor as its not needed anymore once connection was upgraded to websockets
        ctx.pipeline().remove(handler);
      }
      ws = new WebSocketImpl(vertx, ClientConnection.this, supportsContinuation, client.getOptions().getMaxWebsocketFrameSize());
      handshaker.finishHandshake(channel, response);
      log.debug("WebSocket handshake complete");
      wsConnect.handle(ws);
    }
  }

  public void closeHandler(Handler<Void> handler) {
    this.closeHandler = handler;
  }

  boolean isClosed() {
    return !channel.isOpen();
  }

  int getOutstandingRequestCount() {
    return requests.size();
  }

  @Override
  public synchronized void handleInterestedOpsChanged() {
    if (!isNotWritable()) {
      if (currentRequest != null) {
        currentRequest.handleDrained();
      } else if (ws != null) {
        ws.writable();
      }
    }
  }

  void handleResponse(HttpResponse resp) {
    if (resp.getStatus().code() == 100) {
      //If we get a 100 continue it will be followed by the real response later, so we don't remove it yet
      requestForResponse = requests.peek();
    } else {
      requestForResponse = requests.poll();
    }
    if (requestForResponse == null) {
      throw new IllegalStateException("No response handler");
    }
    HttpClientResponseImpl nResp = new HttpClientResponseImpl(vertx, requestForResponse, this, resp);
    currentResponse = nResp;
    requestForResponse.handleResponse(nResp);
  }

  void handleResponseChunk(Buffer buff) {
    currentResponse.handleChunk(buff);
  }

  void handleResponseEnd(LastHttpContent trailer) {
    currentResponse.handleEnd(trailer);

    // We don't signal response end for a 100-continue response as a real response will follow
    // Also we keep the connection open for an HTTP CONNECT
    if (currentResponse.statusCode() != 100 && requestForResponse.getRequest().getMethod() != HttpMethod.CONNECT) {
      listener.responseEnded(this);
    }
  }

  synchronized void handleWsFrame(WebSocketFrameInternal frame) {
    if (ws != null) {
      ws.handleFrame(frame);
    }
  }

  protected synchronized void handleClosed() {
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }
  }

  protected ContextImpl getContext() {
    return super.getContext();
  }

  @Override
  protected synchronized void handleException(Throwable e) {
    super.handleException(e);
    if (currentRequest != null) {
      currentRequest.handleException(e);
    } else if (currentResponse != null) {
      currentResponse.handleException(e);
    }
  }

  synchronized void setCurrentRequest(HttpClientRequestImpl req) {
    if (currentRequest != null) {
      throw new IllegalStateException("Connection is already writing a request");
    }
    this.currentRequest = req;
    this.requests.add(req);
  }

  synchronized void endRequest() {
    if (currentRequest == null) {
      throw new IllegalStateException("No write in progress");
    }
    currentRequest = null;
    listener.requestEnded(this);
  }

  public String hostHeader() {
    return hostHeader;
  }

  @Override
  public synchronized void close() {
    if (handshaker == null) {
      super.close();
    } else {
      // make sure everything is flushed out on close
      endReadAndFlush();
      // close the websocket connection by sending a close frame.
      handshaker.close(channel, new CloseWebSocketFrame(1000, null));
    }
  }

  NetSocket createNetSocket() {
    // connection was upgraded to raw TCP socket
    NetSocketImpl socket = new NetSocketImpl(vertx, channel, context, client.getSslHelper(), true, metrics);
    Map<Channel, NetSocketImpl> connectionMap = new HashMap<>(1);
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
    pipeline.replace("handler", "handler", new VertxNetHandler(client.getVertx(), connectionMap) {
      @Override
      public void exceptionCaught(ChannelHandlerContext chctx, Throwable t) throws Exception {
        // remove from the real mapping
        client.removeChannel(channel);
        super.exceptionCaught(chctx, t);
      }

      @Override
      public void channelInactive(ChannelHandlerContext chctx) throws Exception {
        // remove from the real mapping
        client.removeChannel(channel);
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
