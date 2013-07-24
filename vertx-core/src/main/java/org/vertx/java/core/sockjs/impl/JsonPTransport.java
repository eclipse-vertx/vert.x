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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class JsonPTransport extends BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(JsonPTransport.class);

  JsonPTransport(VertxInternal vertx, RouteMatcher rm, String basePath, final Map<String, Session> sessions, final JsonObject config,
            final Handler<SockJSSocket> sockHandler) {
    super(vertx, sessions, config);

    String jsonpRE = basePath + COMMON_PATH_ELEMENT_RE + "jsonp";

    rm.getWithRegEx(jsonpRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
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
        Session session = getSession(config.getLong("session_timeout"), config.getLong("heartbeat_period"), sessionID, sockHandler);
        session.register(new JsonPListener(req, session, callback));
      }
    });

    String jsonpSendRE = basePath + COMMON_PATH_ELEMENT_RE + "jsonp_send";

    rm.postWithRegEx(jsonpSendRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("JsonP, post: " + req.uri());
        String sessionID = req.params().get("param0");
        final Session session = sessions.get(sessionID);
        if (session != null) {
          handleSend(req, session);
        } else {
          req.response().setStatusCode(404);
          setJSESSIONID(config, req);
          req.response().end();
        }
      }
    });
  }

  private void handleSend(final HttpServerRequest req, final Session session) {
    req.bodyHandler(new Handler<Buffer>() {

      public void handle(Buffer buff) {
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
      }
    });
  }

  private class JsonPListener extends BaseListener {

    final HttpServerRequest req;
    final Session session;
    final String callback;
    boolean headersWritten;
    boolean closed;

    JsonPListener(HttpServerRequest req, Session session, String callback) {
      this.req = req;
      this.session = session;
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
