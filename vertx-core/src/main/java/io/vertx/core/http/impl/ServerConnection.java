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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VoidHandler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxNetHandler;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ServerConnection extends ConnectionBase {

  private static final int CHANNEL_PAUSE_QUEUE_SIZE = 5;

  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private HttpServerRequestImpl currentRequest;
  private HttpServerResponseImpl pendingResponse;
  private ServerWebSocketImpl ws;
  private boolean channelPaused;
  private boolean paused;
  private boolean sentCheck;
  private final Queue<Object> pending = new ArrayDeque<>(8);
  private final String serverOrigin;
  private final HttpServerImpl server;
  private final WebSocketServerHandshaker handshaker;
  private ChannelFuture lastWriteFuture;

  ServerConnection(VertxInternal vertx, HttpServerImpl server, Channel channel, ContextImpl context, String serverOrigin, WebSocketServerHandshaker handshaker) {
    super(vertx, channel, context);
    this.serverOrigin = serverOrigin;
    this.server = server;
    this.handshaker = handshaker;
  }

  public void pause() {
    if (!paused) {
      paused = true;
    }
  }

  public void resume() {
    if (paused) {
      paused = false;
      checkNextTick();
    }
  }

  void handleMessage(Object msg) {
    if (paused || (pendingResponse != null && msg instanceof HttpRequest) || !pending.isEmpty()) {
      //We queue requests if paused or a request is in progress to prevent responses being written in the wrong order
      pending.add(msg);
      if (pending.size() == CHANNEL_PAUSE_QUEUE_SIZE) {
        //We pause the channel too, to prevent the queue growing too large, but we don't do this
        //until the queue reaches a certain size, to avoid pausing it too often
        super.doPause();
        channelPaused = true;
      }
    } else {
      processMessage(msg);
    }
  }

  void responseComplete() {
    pendingResponse = null;
    checkNextTick();
  }

  void requestHandler(Handler<HttpServerRequest> handler) {
    this.requestHandler = handler;
  }

  void wsHandler(Handler<ServerWebSocket> handler) {
    this.wsHandler = handler;
  }

  String getServerOrigin() {
    return serverOrigin;
  }


  Vertx vertx() {
    return vertx;
  }

  @Override
  public ChannelFuture writeToChannel(Object obj) {
    return lastWriteFuture = super.writeToChannel(obj);
  }

  NetSocket createNetSocket() {
    NetSocketImpl socket = new NetSocketImpl(vertx, channel, context, server.getSslHelper(), false);
    Map<Channel, NetSocketImpl> connectionMap = new HashMap<Channel, NetSocketImpl>(1);
    connectionMap.put(channel, socket);

    // Flush out all pending data
    endReadAndFlush();

    // remove old http handlers and replace the old handler with one that handle plain sockets
    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler compressor = pipeline.get(HttpChunkContentCompressor.class);
    if (compressor != null) {
      pipeline.remove(compressor);
    }

    pipeline.remove("httpDecoder");
    if (pipeline.get("chunkedWriter") != null) {
      pipeline.remove("chunkedWriter");
    }

    channel.pipeline().replace("handler", "handler", new VertxNetHandler(vertx, connectionMap) {
      @Override
      public void exceptionCaught(ChannelHandlerContext chctx, Throwable t) throws Exception {
        // remove from the real mapping
        server.removeChannel(channel);
        super.exceptionCaught(chctx, t);
      }

      @Override
      public void channelInactive(ChannelHandlerContext chctx) throws Exception {
        // remove from the real mapping
        server.removeChannel(channel);
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

    // check if the encoder can be removed yet or not.
    if (lastWriteFuture == null) {
      channel.pipeline().remove("httpEncoder");
    } else {
      lastWriteFuture.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          channel.pipeline().remove("httpEncoder");
        }
      });
    }
    return socket;
  }

  private void handleRequest(HttpServerRequestImpl req, HttpServerResponseImpl resp) {
    this.currentRequest = req;
    pendingResponse = resp;
    if (requestHandler != null) {
      requestHandler.handle(req);
    }
  }

  private void handleChunk(Buffer chunk) {
    currentRequest.handleData(chunk);
  }

  private void handleEnd() {
    currentRequest.handleEnd();
    currentRequest = null;
  }

  @Override
  public void handleInterestedOpsChanged() {
    if (!isNotWritable()) {
      if (pendingResponse != null) {
        pendingResponse.handleDrained();
      } else if (ws != null) {
        ws.writable();
      }
    }
  }

  void handleWebsocketConnect(ServerWebSocketImpl ws) {
    if (wsHandler != null) {
      wsHandler.handle(ws);
      this.ws = ws;
    }
  }

  private void handleWsFrame(WebSocketFrameInternal frame) {
    if (ws != null) {
      ws.handleFrame(frame);
    }
  }

  protected void handleClosed() {
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }
    if (pendingResponse != null) {
      pendingResponse.handleClosed();
    }
  }

  protected ContextImpl getContext() {
    return super.getContext();
  }

  @Override
  protected void handleException(Throwable t) {
    super.handleException(t);
    if (currentRequest != null) {
      currentRequest.handleException(t);
    }
    if (pendingResponse != null) {
      pendingResponse.handleException(t);
    }
    if (ws != null) {
      ws.handleException(t);
    }
  }

  protected void addFuture(Handler<AsyncResult<Void>> completionHandler, ChannelFuture future) {
    super.addFuture(completionHandler, future);
  }

  @Override
  protected boolean supportsFileRegion() {
    return super.supportsFileRegion() && channel.pipeline().get(HttpChunkContentCompressor.class) == null;
  }

  protected ChannelFuture sendFile(File file) {
    return super.sendFile(file);
  }

  @Override
  public void close() {
    if (handshaker == null) {
      super.close();
    } else {
      endReadAndFlush();
      handshaker.close(channel, new CloseWebSocketFrame(1000, null));
    }
  }

  private void processMessage(Object msg) {
    if (msg instanceof HttpRequest) {
      HttpRequest request = (HttpRequest) msg;
      HttpServerResponseImpl resp = new HttpServerResponseImpl(vertx, this, request);
      HttpServerRequestImpl req = new HttpServerRequestImpl(this, request, resp);
      handleRequest(req, resp);
    }
    if (msg instanceof HttpContent) {
        HttpContent chunk = (HttpContent) msg;
      if (chunk.content().isReadable()) {
        Buffer buff = Buffer.buffer(chunk.content());
        handleChunk(buff);
      }

      //TODO chunk trailers
      if (msg instanceof LastHttpContent) {
        if (!paused) {
          handleEnd();
        } else {
          // Requeue
          pending.add(LastHttpContent.EMPTY_LAST_CONTENT);
        }
      }
    } else if (msg instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
      handleWsFrame(frame);
    }

    checkNextTick();
  }

  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!pending.isEmpty() && !sentCheck && !paused && (pendingResponse == null || pending.peek() instanceof HttpContent)) {
      sentCheck = true;
      vertx.runOnContext(new VoidHandler() {
        public void handle() {
          sentCheck = false;
          if (!paused) {
            Object msg = pending.poll();
            if (msg != null) {
              processMessage(msg);
            }
            if (channelPaused && pending.isEmpty()) {
              //Resume the actual channel
              ServerConnection.super.doResume();
              channelPaused = false;
            }
          }
        }
      });
    }
  }
}
