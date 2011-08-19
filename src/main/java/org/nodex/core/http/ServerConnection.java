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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.nodex.core.buffer.Buffer;

import java.io.File;

class ServerConnection extends AbstractConnection {

  private HttpRequestHandler mainHandler;
  private WebsocketConnectHandler wsHandler;

  private HttpServerRequest currentRequest;
  private HttpServerResponse currentResponse;
  private Websocket ws;

  private boolean paused;

  ServerConnection(Channel channel, long contextID, Thread th) {
    super(channel, contextID, th);
  }

  void requestHandler(HttpRequestHandler handler) {
    this.mainHandler = handler;
  }

  void wsHandler(WebsocketConnectHandler handler) {
    this.wsHandler = handler;
  }

  //Close without checking thread - used when server is closed
  void internalClose() {
    channel.close();
  }

  void handleRequest(HttpServerRequest req) {
    setContextID();
    try {
      this.currentRequest = req;
      this.currentResponse = req.response;
      if (mainHandler != null) {
        mainHandler.onRequest(req);
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleChunk(Buffer chunk) {
    try {
      if (currentRequest != null) {
        setContextID();
        currentRequest.handleData(chunk);
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleEnd() {
    try {
      setContextID();
      currentRequest.handleEnd();
      if (currentResponse != null) {
        pause();
        paused = true;
      }
      currentRequest = null;
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleInterestedOpsChanged() {
    try {
      if ((channel.getInterestOps() & Channel.OP_WRITE) == Channel.OP_WRITE) {
        setContextID();
        if (currentResponse != null) {
          currentResponse.writable();
        } else if (ws != null) {
          ws.writable();
        }
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  boolean handleWebsocketConnect(Websocket ws) {
    try {
      if (wsHandler != null) {
        setContextID();
        if (wsHandler.onConnect(ws)) {
          this.ws = ws;
          return true;
        }
      }
      return false;
    } catch (Throwable t) {
      handleHandlerException(t);
      return false;
    }
  }

  void handleWsFrame(WebSocketFrame frame) {
    try {
      if (ws != null) {
        setContextID();
        ws.handleFrame(frame);
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  /*
  If the request endHandler completes and the response has not been ended then we want to pause and resume when it is complete
  to avoid responses for the same connection being written out of order
   */
  void responseComplete() {
    if (paused) {
      resume();
      paused = false;
    }
    currentResponse = null;
  }

  protected void handleClosed() {
    super.handleClosed();
  }

  protected long getContextID() {
    return super.getContextID();
  }

  protected void handleException(Exception e) {
    super.handleException(e);
    if (currentRequest != null) {
      currentRequest.handleException(e);
    }
    if (currentResponse != null) {
      currentResponse.handleException(e);
    }
    if (ws != null) {
      ws.handleException(e);
    }
  }

  protected void addFuture(Runnable done, ChannelFuture future) {
    super.addFuture(done, future);
  }

  protected boolean isSSL() {
    return super.isSSL();
  }

  protected ChannelFuture sendFile(File file) {
    return super.sendFile(file);
  }
}
