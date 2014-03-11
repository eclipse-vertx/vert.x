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
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.impl.StringEscapeUtils;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(BaseTransport.class);

  protected final VertxInternal vertx;
  protected final Map<String, Session> sessions;
  protected JsonObject config;

  protected static final String COMMON_PATH_ELEMENT_RE = "\\/[^\\/\\.]+\\/([^\\/\\.]+)\\/";

  private static final long RAND_OFFSET = 2L << 30;

  public BaseTransport(VertxInternal vertx, Map<String, Session> sessions, JsonObject config) {
    this.vertx = vertx;
    this.sessions = sessions;
    this.config = config;
  }

  protected Session getSession(final long timeout, final long heartbeatPeriod, final String sessionID,
                               Handler<SockJSSocket> sockHandler) {
    Session session = sessions.get(sessionID);
    if (session == null) {
      session = new Session(vertx, sessions, sessionID, timeout, heartbeatPeriod, sockHandler);
      sessions.put(sessionID, session);
    }
    return session;
  }

  protected void sendInvalidJSON(HttpServerResponse response) {
    if (log.isTraceEnabled()) log.trace("Broken JSON");
    response.setStatusCode(500);
    response.end("Broken JSON encoding.");
  }

  protected String escapeForJavaScript(String str) {
    try {
       str = StringEscapeUtils.escapeJavaScript(str);
    } catch (Exception e) {
      log.error("Failed to escape", e);
      str = null;
    }
    return str;
  }

  protected static abstract class BaseListener implements TransportListener {
    protected final HttpServerRequest req;
    protected final Session session;
    protected boolean closed;

    protected BaseListener(final HttpServerRequest req, final Session session) {
      this.req = req;
      this.session = session;
    }
    protected void addCloseHandler(HttpServerResponse resp, final Session session) {
      resp.closeHandler(new VoidHandler() {
        public void handle() {
          if (log.isTraceEnabled()) log.trace("Connection closed (from client?), closing session");
          // Connection has been closed from the client or network error so
          // we remove the session
          session.shutdown();
          closed = true;
        }
      });
    }

    @Override
    public void sessionClosed() {
      session.writeClosed(this);
      close();
    }
  }

  static void setJSESSIONID(JsonObject config, HttpServerRequest req) {
    String cookies = req.headers().get("cookie");
    if (config.getBoolean("insert_JSESSIONID")) {
      //Preserve existing JSESSIONID, if any
      if (cookies != null) {
        String[] parts;
        if (cookies.contains(";")) {
          parts = cookies.split(";");
        } else {
          parts = new String[] {cookies};
        }
        for (String part: parts) {
          if (part.startsWith("JSESSIONID")) {
            cookies = part + "; path=/";
            break;
          }
        }
      }
      if (cookies == null) {
        cookies = "JSESSIONID=dummy; path=/";
      }
      req.response().headers().set("Set-Cookie", cookies);
    }
  }

  static void setCORS(HttpServerRequest req) {
    String origin = req.headers().get("origin");
    if (origin == null || "null".equals(origin)) {
      origin = "*";
    }
    req.response().headers().set("Access-Control-Allow-Origin", origin);
    req.response().headers().set("Access-Control-Allow-Credentials", "true");
    String hdr = req.headers().get("Access-Control-Request-Headers");
    if (hdr != null) {
      req.response().headers().set("Access-Control-Allow-Headers", hdr);
    }
  }

  static Handler<HttpServerRequest> createInfoHandler(final JsonObject config) {
    return new Handler<HttpServerRequest>() {
      boolean websocket = !config.getArray("disabled_transports").contains(Transport.WEBSOCKET.toString());
      public void handle(HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("In Info handler");
        req.response().headers().set("Content-Type", "application/json; charset=UTF-8");
        setNoCacheHeaders(req);
        JsonObject json = new JsonObject();
        json.putBoolean("websocket", websocket);
        json.putBoolean("cookie_needed", config.getBoolean("insert_JSESSIONID"));
        json.putArray("origins", new JsonArray().add("*:*"));
        // Java ints are signed, so we need to use a long and add the offset so
        // the result is not negative
        json.putNumber("entropy", RAND_OFFSET + new Random().nextInt());
        setCORS(req);
        req.response().end(json.encode());
      }
    };
  }

  static void setNoCacheHeaders(HttpServerRequest req) {
    req.response().headers().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
  }

  static Handler<HttpServerRequest> createCORSOptionsHandler(final JsonObject config, final String methods) {
    return new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("In CORS options handler");
        req.response().headers().set("Cache-Control", "public,max-age=31536000");
        long oneYearSeconds = 365 * 24 * 60 * 60;
        long oneYearms = oneYearSeconds * 1000;
        String expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(System.currentTimeMillis() + oneYearms));
        req.response().headers().set("Expires", expires);
        req.response().headers().set("Access-Control-Allow-Methods", methods);
        req.response().headers().set("Access-Control-Max-Age", String.valueOf(oneYearSeconds));
        setCORS(req);
        setJSESSIONID(config, req);
        req.response().setStatusCode(204);
        req.response().end();
      }
    };
  }

  // We remove cookie headers for security reasons. See https://github.com/sockjs/sockjs-node section on
  // Authorisation
  static MultiMap removeCookieHeaders(MultiMap headers) {
    // We don't want to remove the JSESSION cookie.
    String jsessionid = null;
    for (String cookie : headers.getAll("cookie")) {
      if (cookie.startsWith("JSESSIONID=")) {
        jsessionid = cookie;
      }
    }
    headers.remove("cookie");

    // Add back jsessionid cookie header
    if (jsessionid != null) {
      headers.add("cookie", jsessionid);
    }

    return headers;
  }
}
