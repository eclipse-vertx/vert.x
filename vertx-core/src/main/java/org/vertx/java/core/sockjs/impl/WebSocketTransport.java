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

package org.vertx.java.core.sockjs.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.ServerWebSocket;
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
        if (log.isTraceEnabled()) log.trace("WS, handler");
        final Session session = new Session(vertx, sessions, config.getLong("heartbeat_period"), sockHandler);
        session.setInfo(match.ws.localAddress(), match.ws.remoteAddress(), match.ws.uri(), match.ws.headers());
        session.register(new WebSocketListener(match.ws, session));
      }
    });

    rm.getWithRegEx(wsRE, new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest request) {
        if (log.isTraceEnabled()) log.trace("WS, get: " + request.uri());
        request.response().setStatusCode(400);
        request.response().end("Can \"Upgrade\" only to \"WebSocket\".");
      }
    });

    rm.allWithRegEx(wsRE, new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest request) {
        if (log.isTraceEnabled()) log.trace("WS, all: " + request.uri());
        request.response().headers().set("Allow", "GET");
        request.response().setStatusCode(405);
        request.response().end();
      }
    });
  }

  private static class WebSocketListener implements TransportListener {

    final ServerWebSocket ws;
    final Session session;
    boolean closed;

    WebSocketListener(final ServerWebSocket ws, final Session session) {
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
      ws.closeHandler(new VoidHandler() {
        public void handle() {
          closed = true;
          session.shutdown();
        }
      });
      ws.exceptionHandler(new Handler<Throwable>() {
        public void handle(Throwable t) {
          closed = true;
          session.shutdown();
          session.handleException(t);
          }
      });
    }

    public void sendFrame(final String body) {
      if (log.isTraceEnabled()) log.trace("WS, sending frame");
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

    public void sessionClosed() {
      session.writeClosed(this);
      closed = true;
      ws.close();
    }

  }
}
