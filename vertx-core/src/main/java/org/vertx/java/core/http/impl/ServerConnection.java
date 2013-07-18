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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.ConnectionBase;
import org.vertx.java.core.net.impl.DefaultNetSocket;
import org.vertx.java.core.net.impl.VertxNetHandler;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ServerConnection extends ConnectionBase {

  private static final int CHANNEL_PAUSE_QUEUE_SIZE = 5;

  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private DefaultHttpServerRequest currentRequest;
  private DefaultHttpServerResponse pendingResponse;
  private DefaultServerWebSocket ws;
  private boolean channelPaused;
  private boolean paused;
  private boolean sentCheck;
  private final Queue<Object> pending = new LinkedList<>();
  private final String serverOrigin;
  private final DefaultHttpServer server;
  private ChannelFuture lastWriteFuture;

  ServerConnection(DefaultHttpServer server, Channel channel, DefaultContext context, String serverOrigin) {
    super(server.vertx, channel, context);
    this.serverOrigin = serverOrigin;
    this.server = server;
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
    if (paused || (msg instanceof HttpRequest && pendingResponse != null) || !pending.isEmpty()) {
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
  public ChannelFuture write(Object obj) {
    ChannelFuture future = lastWriteFuture = super.write(obj);
    return future;
  }

  NetSocket createNetSocket() {
    DefaultNetSocket socket = new DefaultNetSocket(vertx, channel, context);
    Map<Channel, DefaultNetSocket> connectionMap = new HashMap<Channel, DefaultNetSocket>(1);
    connectionMap.put(channel, socket);

    // Flush out all pending data
    endReadAndFlush();

    // remove old http handlers and replace the old handler with one that handle plain sockets
    channel.pipeline().remove("httpDecoder");
    if (channel.pipeline().get("chunkedWriter") != null) {
      channel.pipeline().remove("chunkedWriter");
    }
    channel.pipeline().replace("handler", "handler", new VertxNetHandler(server.vertx, connectionMap) {
      @Override
      public void exceptionCaught(ChannelHandlerContext chctx, Throwable t) throws Exception {
        // remove from the real mapping
        server.connectionMap.remove(channel);
        super.exceptionCaught(chctx, t);
      }

      @Override
      public void channelInactive(ChannelHandlerContext chctx) throws Exception {
        // remove from the real mapping
        server.connectionMap.remove(channel);
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

  private void handleRequest(DefaultHttpServerRequest req, DefaultHttpServerResponse resp) {
    setContext();
    try {
      this.currentRequest = req;
      pendingResponse = resp;
      if (requestHandler != null) {
        requestHandler.handle(req);
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  private void handleChunk(Buffer chunk) {
    try {
      setContext();
      currentRequest.handleData(chunk);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  private void handleEnd() {
    try {
      setContext();
      currentRequest.handleEnd();
      currentRequest = null;
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  @Override
  public void handleInterestedOpsChanged() {
    try {
      if (!doWriteQueueFull()) {
        setContext();
        if (pendingResponse != null) {
          pendingResponse.handleDrained();
        } else if (ws != null) {
          ws.writable();
        }
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleWebsocketConnect(DefaultServerWebSocket ws) {
    try {
      if (wsHandler != null) {
        setContext();
        wsHandler.handle(ws);
        this.ws = ws;
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  private void handleWsFrame(WebSocketFrame frame) {
    try {
      if (ws != null) {
        setContext();
        ws.handleFrame(frame);
      }
    } catch (Throwable t) {
      handleHandlerException(t);
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

  protected DefaultContext getContext() {
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

  protected void addFuture(Handler<AsyncResult<Void>> doneHandler, ChannelFuture future) {
    super.addFuture(doneHandler, future);
  }

  protected boolean isSSL() {
    return super.isSSL();
  }

  protected ChannelFuture sendFile(File file) {
    return super.sendFile(file);
  }

  private void processMessage(Object msg) {
    if (msg instanceof HttpRequest) {
      HttpRequest request = (HttpRequest) msg;
      DefaultHttpServerResponse resp = new DefaultHttpServerResponse(vertx, this, request);
      DefaultHttpServerRequest req = new DefaultHttpServerRequest(this, request, resp);
      handleRequest(req, resp);
    }
    if (msg instanceof HttpContent) {
        HttpContent chunk = (HttpContent) msg;
      if (chunk.content().isReadable()) {
        Buffer buff = new Buffer(chunk.content());
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
    } else if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;
      handleWsFrame(frame);
    }

    checkNextTick();
  }

  // TODO I think this can be simplified / optimised
  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!sentCheck && !pending.isEmpty() && !paused && (pendingResponse == null || pending.peek() instanceof HttpContent)) {
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
