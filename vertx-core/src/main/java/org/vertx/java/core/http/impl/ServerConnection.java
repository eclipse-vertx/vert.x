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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ServerConnection extends AbstractConnection {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ServerConnection.class);

  private static final int CHANNEL_PAUSE_QUEUE_SIZE = 5;

  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private DefaultHttpServerRequest currentRequest;
  private DefaultHttpServerResponse pendingResponse;
  private DefaultWebSocket ws;
  private boolean channelPaused;
  private boolean paused;
  private boolean sentCheck;
  private final Queue<Object> pending = new LinkedList<>();

  ServerConnection(VertxInternal vertx, Channel channel, Context context) {
    super(vertx, channel, context);
  }

  @Override
  public void pause() {
    if (!paused) {
      paused = true;
    }
  }

  @Override
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
        super.pause();
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

  //Close without checking thread - used when server is closed
  void internalClose() {
    channel.close();
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

  void handleInterestedOpsChanged() {
    try {
      if (channel.isWritable()) {
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

  void handleWebsocketConnect(DefaultWebSocket ws) {
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

  protected Context getContext() {
    return super.getContext();
  }

  protected void handleException(Exception e) {
    super.handleException(e);
    if (currentRequest != null) {
      currentRequest.handleException(e);
    }
    if (pendingResponse != null) {
      pendingResponse.handleException(e);
    }
    if (ws != null) {
      ws.handleException(e);
    }
  }

  protected void addFuture(Handler<Void> doneHandler, ChannelFuture future) {
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

      String method = request.getMethod().toString();
      URI theURI;
      try {
        theURI = new URI(request.getUri());
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid uri " + request.getUri()); //Should never happen
      }
      String uri = request.getUri();
      String path = theURI.getPath();
      String query = theURI.getQuery();
      HttpVersion ver = request.getProtocolVersion();
      boolean keepAlive = ver == HttpVersion.HTTP_1_1 ||
          (ver == HttpVersion.HTTP_1_0 && "Keep-Alive".equalsIgnoreCase(request.getHeader("Connection")));
      DefaultHttpServerResponse resp = new DefaultHttpServerResponse(vertx, this, request.getProtocolVersion(), keepAlive);
      DefaultHttpServerRequest req = new DefaultHttpServerRequest(this, method, uri, path, query, resp, request);
      handleRequest(req, resp);

      ChannelBuffer requestBody = request.getContent();

      if (requestBody.readable()) {
        if (!paused) {
          handleChunk(new Buffer(requestBody));
        } else {
          // We need to requeue it
          pending.add(new DefaultHttpChunk(requestBody));
        }
      }
      if (!request.isChunked()) {
        if (!paused) {
          handleEnd();
        } else {
          // Requeue
          pending.add(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));
        }
      }
    } else if (msg instanceof HttpChunk) {
      HttpChunk chunk = (HttpChunk) msg;
      if (chunk.getContent().readable()) {
        Buffer buff = new Buffer(chunk.getContent());
        handleChunk(buff);
      }

      //TODO chunk trailers
      if (chunk.isLast()) {
        if (!paused) {
          handleEnd();
        } else {
          // Requeue
          pending.add(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));
        }
      }
    } else if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;
      handleWsFrame(frame);
    }

    checkNextTick();
  }

  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!sentCheck && !pending.isEmpty() && !paused && (pendingResponse == null || pending.peek() instanceof HttpChunk)) {
      sentCheck = true;
      vertx.runOnLoop(new SimpleHandler() {
        public void handle() {
          sentCheck = false;
          if (!paused) {
            Object msg = pending.poll();
            if (msg != null) {
              processMessage(msg);
            }
            if (channelPaused && pending.isEmpty()) {
              //Resume the actual channel
              ServerConnection.super.resume();
              channelPaused = false;
            }
          }
        }
      });
    }
  }
}
