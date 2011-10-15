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

package org.vertx.java.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.ws.WebSocketFrame;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

class ServerConnection extends AbstractConnection {

  private static final int CHANNEL_PAUSE_QUEUE_SIZE = 5;

  private Handler<HttpServerRequest> requestHandler;
  private Handler<Websocket> wsHandler;
  private HttpServerRequest currentRequest;
  private boolean pendingResponse;
  private Websocket ws;
  private boolean channelPaused;
  private boolean paused;
  private boolean sentCheck;
  private boolean responded;
  private final Queue<Object> pending = new LinkedList<>();

  ServerConnection(Channel channel, long contextID, Thread th) {
    super(channel, contextID, th);
  }

  @Override
  public void pause() {
    checkThread();
    if (!paused) {
      paused = true;
    }
  }

  @Override
  public void resume() {
    checkThread();
    if (paused) {
      paused = false;
      checkNextTick();
    }
  }

  public boolean isResponded(){
      return responded;
  }

  void handleMessage(Object msg) {
    if (paused || (msg instanceof HttpRequest && pendingResponse) || !pending.isEmpty()) {
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
    pendingResponse = false;
    responded = true;
    checkNextTick();
  }

  void requestHandler(Handler<HttpServerRequest> handler) {
    this.requestHandler = handler;
  }

  void wsHandler(Handler<Websocket> handler) {
    this.wsHandler = handler;
  }

  //Close without checking thread - used when server is closed
  void internalClose() {
    channel.close();
  }

  private void handleRequest(HttpServerRequest req) {
    setContextID();
    try {
      this.currentRequest = req;
      pendingResponse = true;

      if (requestHandler != null) {
        requestHandler.handle(req);
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  private void handleChunk(Buffer chunk) {
    try {
      setContextID();
      currentRequest.handleData(chunk);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  private void handleEnd() {
    try {
      setContextID();
      currentRequest.handleEnd();
      currentRequest = null;
      responded = false; // reset the responded state of serverConnectionObject
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleInterestedOpsChanged() {
    try {
      if ((channel.getInterestOps() & Channel.OP_WRITE) == Channel.OP_WRITE) {
        setContextID();
        if (currentRequest != null) {
          currentRequest.response.writable();
        } else if (ws != null) {
          ws.writable();
        }
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleWebsocketConnect(String uri) {
    try {
      if (wsHandler != null) {
        setContextID();
        Websocket ws = new Websocket(uri, this);
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
        setContextID();
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
  }

  protected long getContextID() {
    return super.getContextID();
  }

  protected void handleException(Exception e) {
    super.handleException(e);
    if (currentRequest != null) {
      currentRequest.handleException(e);
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
      HttpServerRequest req = new HttpServerRequest(this, request);
      handleRequest(req);
      ChannelBuffer requestBody = request.getContent();

      if (requestBody.readable()) {
        handleChunk(new Buffer(requestBody));
      }
      if (!request.isChunked()) {
        handleEnd();
      }
    } else if (msg instanceof HttpChunk) {
      HttpChunk chunk = (HttpChunk) msg;
      if (chunk.getContent().readable()) {
        Buffer buff = new Buffer(chunk.getContent());
        handleChunk(buff);
      }
      //TODO chunk trailers
      if (chunk.isLast()) {
        handleEnd();
      }
    } else if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;
      handleWsFrame(frame);
    }

    checkNextTick();
  }

  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!sentCheck && !pending.isEmpty() && !paused && (!pendingResponse || pending.peek() instanceof HttpChunk)) {
      sentCheck = true;
      Vertx.instance.nextTick(new SimpleHandler() {
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
