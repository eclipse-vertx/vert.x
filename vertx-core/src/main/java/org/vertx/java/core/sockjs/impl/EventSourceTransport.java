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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class EventSourceTransport extends BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(EventSourceTransport.class);

  EventSourceTransport(VertxInternal vertx,RouteMatcher rm, String basePath, Map<String, Session> sessions, final JsonObject config,
            final Handler<SockJSSocket> sockHandler) {
    super(vertx, sessions, config);

    String eventSourceRE = basePath + COMMON_PATH_ELEMENT_RE + "eventsource";

    rm.getWithRegEx(eventSourceRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("EventSource transport, get: " + req.uri());
        String sessionID = req.params().get("param0");
        Session session = getSession(config.getLong("session_timeout"), config.getLong("heartbeat_period"), sessionID, sockHandler);
        session.setInfo(req.localAddress(), req.remoteAddress(), req.uri(), req.headers());
        session.register(new EventSourceListener(config.getInteger("max_bytes_streaming"), req, session));
      }
    });
  }

  private class EventSourceListener extends BaseListener {

    final int maxBytesStreaming;

    boolean headersWritten;
    int bytesSent;
    boolean closed;

    EventSourceListener(int maxBytesStreaming, HttpServerRequest req, Session session) {
      super(req, session);
      this.maxBytesStreaming = maxBytesStreaming;
      addCloseHandler(req.response(), session);
    }

    public void sendFrame(String body) {
      if (log.isTraceEnabled()) log.trace("EventSource, sending frame");
      if (!headersWritten) {
        req.response().headers().set("Content-Type", "text/event-stream; charset=UTF-8");
        setNoCacheHeaders(req);
        setJSESSIONID(config, req);
        req.response().setChunked(true);
        req.response().write("\r\n");
        headersWritten = true;
      }
      StringBuilder sb = new StringBuilder();
      sb.append("data: ");
      sb.append(body);
      sb.append("\r\n\r\n");
      Buffer buff = new Buffer(sb.toString());
      req.response().write(buff);
      bytesSent += buff.length();
      if (bytesSent >= maxBytesStreaming) {
        if (log.isTraceEnabled()) log.trace("More than maxBytes sent so closing connection");
        // Reset and close the connection
        close();
      }
    }

    public void close() {
      if (!closed) {
        try {
          session.resetListener();
          req.response().end();
          req.response().close();
        } catch (IllegalStateException e) {
          // Underlying connection might already be closed - that's fine
        }
        closed = true;
      }
    }

  }
}
