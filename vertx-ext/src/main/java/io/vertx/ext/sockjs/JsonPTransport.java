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

package io.vertx.ext.sockjs;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RouteMatcher;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class JsonPTransport extends BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(JsonPTransport.class);

  JsonPTransport(Vertx vertx, RouteMatcher rm, String basePath, final Map<String, Session> sessions, final JsonObject config,
            final Handler<SockJSSocket> sockHandler) {
    super(vertx, sessions, config);

    String jsonpRE = basePath + COMMON_PATH_ELEMENT_RE + "jsonp";

    rm.getWithRegEx(jsonpRE, req -> {
      if (log.isTraceEnabled()) log.trace("JsonP, get: " + req.uri());
      String callback = req.params().get("callback");
      if (callback == null) {
        callback = req.params().get("c");
        if (callback == null) {
          req.response().setStatusCode(500);
          req.response().end("\"callback\" parameter required\n");
          return;
        }
      }

      String sessionID = req.params().get("param0");
      Session session = getSession(config.getLong("session_timeout"), config.getLong("heartbeat_period"), sessionID, sockHandler, req);
      session.register(new JsonPListener(req, session, callback));
    });

    String jsonpSendRE = basePath + COMMON_PATH_ELEMENT_RE + "jsonp_send";

    rm.postWithRegEx(jsonpSendRE, req -> {
      if (log.isTraceEnabled()) log.trace("JsonP, post: " + req.uri());
      String sessionID = req.params().get("param0");
      final Session session = sessions.get(sessionID);
      if (session != null && !session.isClosed()) {
        handleSend(req, session);
      } else {
        req.response().setStatusCode(404);
        setJSESSIONID(config, req);
        req.response().end();
      }
    });
  }

  private void handleSend(final HttpServerRequest req, final Session session) {
    req.bodyHandler(buff -> {
      String body = buff.toString();

      boolean urlEncoded;
      String ct = req.headers().get("content-type");
      if ("application/x-www-form-urlencoded".equalsIgnoreCase(ct)) {
        urlEncoded = true;
      } else if ("text/plain".equalsIgnoreCase(ct)) {
        urlEncoded = false;
      } else {
        req.response().setStatusCode(500);
        req.response().end("Invalid Content-Type");
        return;
      }

      if (body.equals("") || urlEncoded && (!body.startsWith("d=") || body.length() <= 2)) {
        req.response().setStatusCode(500);
        req.response().end("Payload expected.");
        return;
      }

      if (urlEncoded) {
        try {
          body = URLDecoder.decode(body, "UTF-8");
        } catch (UnsupportedEncodingException e) {
          throw new IllegalStateException("No UTF-8!");
        }
        body = body.substring(2);
      }

      if (!session.handleMessages(body)) {
        sendInvalidJSON(req.response());
      } else {
        setJSESSIONID(config, req);
        req.response().headers().set("Content-Type", "text/plain; charset=UTF-8");
        setNoCacheHeaders(req);
        req.response().end("ok");
        if (log.isTraceEnabled()) log.trace("send handled ok");
      }
    });
  }

  private class JsonPListener extends BaseListener {

    final String callback;
    boolean headersWritten;
    boolean closed;

    JsonPListener(HttpServerRequest req, Session session, String callback) {
      super(req, session);
      this.callback = callback;
      addCloseHandler(req.response(), session);
    }


    public void sendFrame(String body) {

      if (log.isTraceEnabled()) log.trace("JsonP, sending frame");

      if (!headersWritten) {
        req.response().setChunked(true);
        req.response().headers().set("Content-Type", "application/javascript; charset=UTF-8");
        setNoCacheHeaders(req);
        setJSESSIONID(config, req);
        headersWritten = true;
      }

      body = escapeForJavaScript(body);

      StringBuilder sb = new StringBuilder();
      sb.append(callback).append("(\"");
      sb.append(body);
      sb.append("\");\r\n");

      //End the response and close the HTTP connection

      req.response().write(sb.toString());
      close();
    }

    public void close() {
      if (!closed) {
        try {
          session.resetListener();
          req.response().end();
          req.response().close();
          closed = true;
        } catch (IllegalStateException e) {
          // Underlying connection might already be closed - that's fine
        }
      }
    }
  }
}
