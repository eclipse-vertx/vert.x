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
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.impl.WebSocketMatcher;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class WebSocketTransport extends BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(WebSocketTransport.class);

  WebSocketTransport(final VertxInternal vertx, WebSocketMatcher wsMatcher,
                     RouteMatcher rm, String basePath, final Map<String, Session> sessions,
                     final JsonObject config,
            final Handler<SockJSSocket> sockHandler) {
    super(vertx, sessions, config);
    String wsRE = basePath + COMMON_PATH_ELEMENT_RE + "websocket";

    wsMatcher.addRegEx(wsRE, new Handler<WebSocketMatcher.Match>() {

      public void handle(final WebSocketMatcher.Match match) {
        final Session session = new Session(vertx, sessions, (Long)config.getNumber("heartbeat_period"), sockHandler);
        session.register(new WebSocketListener(match.ws, session));
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

  private static class WebSocketListener implements TransportListener {

    final WebSocket ws;
    final Session session;
    boolean closed;

    WebSocketListener(final WebSocket ws, final Session session) {
      this.ws = ws;
      this.session = session;
      ws.dataHandler(new Handler<Buffer>() {
        public void handle(Buffer data) {
          if (!session.isClosed()) {
            String msgs = data.toString();
            if (msgs.equals("")) {
              //Ignore empty frames
            } else if ((msgs.startsWith("[\"") && msgs.endsWith("\"]")) ||
                       (msgs.startsWith("\"") && msgs.endsWith("\""))) {
              session.handleMessages(msgs);
            } else {
              //Invalid JSON - we close the connection
              close();
            }
          }
        }
      });
      ws.closedHandler(new SimpleHandler() {
        public void handle() {
          closed = true;
          session.shutdown();
        }
      });
    }

    public void sendFrame(final String body) {
      if (!closed) {
        ws.writeTextFrame(body);
      }
    }

    public void close() {
      if (!closed) {
        ws.close();
        session.shutdown();
        closed = true;
      }
    }
  }
}
