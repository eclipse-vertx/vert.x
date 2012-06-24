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

package org.vertx.java.core.sockjs.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.impl.WebSocketMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.SockJSSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class RawWebSocketTransport {

  private static final Logger log = LoggerFactory.getLogger(WebSocketTransport.class);

  private class RawWSSockJSSocket extends SockJSSocket {

    private WebSocket ws;

    RawWSSockJSSocket(Vertx vertx, WebSocket ws) {
      super(vertx);
      this.ws = ws;
    }

    public void dataHandler(Handler<Buffer> handler) {
      ws.dataHandler(handler);
    }

    public void pause() {
      ws.pause();
    }

    public void resume() {
      ws.resume();
    }

    public void writeBuffer(Buffer data) {
      ws.writeBuffer(data);
    }

    public void setWriteQueueMaxSize(int maxQueueSize) {
      ws.setWriteQueueMaxSize(maxQueueSize);
    }

    public boolean writeQueueFull() {
      return ws.writeQueueFull();
    }

    public void drainHandler(Handler<Void> handler) {
      ws.drainHandler(handler);
    }

    public void exceptionHandler(Handler<Exception> handler) {
      ws.exceptionHandler(handler);
    }

    public void endHandler(Handler<Void> endHandler) {
      ws.endHandler(endHandler);
    }

    public void close() {
      super.close();
      ws.close();
    }

  }

  RawWebSocketTransport(final Vertx vertx, WebSocketMatcher wsMatcher, RouteMatcher rm, String basePath,
                        final Handler<SockJSSocket> sockHandler) {

    String wsRE = basePath + "/websocket";

    wsMatcher.addRegEx(wsRE, new Handler<WebSocketMatcher.Match>() {

      public void handle(final WebSocketMatcher.Match match) {
        SockJSSocket sock = new RawWSSockJSSocket(vertx, match.ws);
        sockHandler.handle(sock);
      }
    });

    rm.getWithRegEx(wsRE, new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest request) {
        request.response.statusCode = 400;
        request.response.end("Can \"Upgrade\" only to \"WebSocket\".");
      }
    });

    rm.allWithRegEx(wsRE, new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest request) {
        request.response.headers().put("Allow", "GET");
        request.response.statusCode = 405;
        request.response.end();
      }
    });
  }

}
