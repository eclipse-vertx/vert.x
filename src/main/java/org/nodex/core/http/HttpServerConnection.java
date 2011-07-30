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

public class HttpServerConnection extends AbstractConnection {

  private HttpRequestHandler mainHandler;
  private HttpRequestHandler getHandler;
  private HttpRequestHandler optionsHandler;
  private HttpRequestHandler headHandler;
  private HttpRequestHandler postHandler;
  private HttpRequestHandler putHandler;
  private HttpRequestHandler deleteHandler;
  private HttpRequestHandler traceHandler;
  private HttpRequestHandler connectHandler;
  private HttpRequestHandler patchHandler;
  private WebsocketConnectHandler wsHandler;

  private HttpServerRequest currentRequest;
  private HttpServerResponse currentResponse;
  private Websocket ws;

  private boolean paused;

  HttpServerConnection(Channel channel, String contextID, Thread th) {
    super(channel, contextID, th);
  }

  public void request(HttpRequestHandler handler) {
    this.mainHandler = handler;
  }

  public void options(HttpRequestHandler handler) {
    this.optionsHandler = handler;
  }

  public void get(HttpRequestHandler handler) {
    this.getHandler = handler;
  }

  public void head(HttpRequestHandler handler) {
    this.headHandler = handler;
  }

  public void post(HttpRequestHandler handler) {
    this.postHandler = handler;
  }

  public void put(HttpRequestHandler handler) {
    this.putHandler = handler;
  }

  public void delete(HttpRequestHandler handler) {
    this.deleteHandler = handler;
  }

  public void trace(HttpRequestHandler handler) {
    this.traceHandler = handler;
  }

  public void connect(HttpRequestHandler handler) {
    this.connectHandler = handler;
  }

  public void patch(HttpRequestHandler handler) {
    this.patchHandler = handler;
  }

  public void websocketConnect(WebsocketConnectHandler handler) {
    this.wsHandler = handler;
  }

  void handleRequest(HttpServerRequest req, HttpServerResponse resp) {
    setContextID();
    try {
      this.currentRequest = req;
      this.currentResponse = resp;
      if (mainHandler != null) {
        mainHandler.onRequest(req, resp);
      } else {
        if (getHandler != null && "GET".equals(req.method)) {
          getHandler.onRequest(req, resp);
        } else if (postHandler != null && "POST".equals(req.method)) {
          postHandler.onRequest(req, resp);
        } else if (putHandler != null && "PUT".equals(req.method)) {
          putHandler.onRequest(req, resp);
        } else if (headHandler != null && "HEAD".equals(req.method)) {
          headHandler.onRequest(req, resp);
        } else if (deleteHandler != null && "DELETE".equals(req.method)) {
          deleteHandler.onRequest(req, resp);
        } else if (traceHandler != null && "TRACE".equals(req.method)) {
          traceHandler.onRequest(req, resp);
        } else if (connectHandler != null && "CONNECT".equals(req.method)) {
          connectHandler.onRequest(req, resp);
        } else if (optionsHandler != null && "OPTIONS".equals(req.method)) {
          optionsHandler.onRequest(req, resp);
        } else if (patchHandler != null && "PATCH".equals(req.method)) {
          patchHandler.onRequest(req, resp);
        }
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
  If the request end completes and the response has not been ended then we want to pause and resume when it is complete
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

  protected String getContextID() {
    return super.getContextID();
  }

  protected void handleException(Exception e) {
    super.handleException(e);
  }

  protected void addFuture(Runnable done, ChannelFuture future) {
    super.addFuture(done, future);
  }

  protected boolean isSSL() {
    return super.isSSL();
  }

  protected void sendFile(File file) {
    super.sendFile(file);
  }
}
