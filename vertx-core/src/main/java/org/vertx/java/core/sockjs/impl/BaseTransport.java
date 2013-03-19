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

  private static final long RAND_OFFSET = 2l << 30;

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
    response.statusCode = 500;
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

    protected void addCloseHandler(HttpServerResponse resp, final Session session) {
      resp.closeHandler(new SimpleHandler() {
        public void handle() {
          if (log.isTraceEnabled()) log.trace("Connection closed (from client?), closing session");
          // Connection has been closed fron the client or network error so
          // we remove the session
          session.shutdown();
          close();
        }
      });
    }

    public void sessionClosed() {
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
      req.response.headers().put("Set-Cookie", cookies);
    }
  }

  static void setCORS(HttpServerRequest req) {
    String origin = req.headers().get("origin");
    if (origin == null || "null".equals(origin)) {
      origin = "*";
    }
    req.response.headers().put("Access-Control-Allow-Origin", origin);
    req.response.headers().put("Access-Control-Allow-Credentials", "true");
    String hdr = req.headers().get("Access-Control-Request-Headers");
    if (hdr != null) {
      req.response.headers().put("Access-Control-Allow-Headers", hdr);
    }
  }

  static Handler<HttpServerRequest> createInfoHandler(final JsonObject config) {
    return new Handler<HttpServerRequest>() {
      boolean websocket = !config.getArray("disabled_transports").contains(Transport.WEBSOCKET.toString());
      public void handle(HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("In Info handler");
        req.response.headers().put("Content-Type", "application/json; charset=UTF-8");
        setNoCacheHeaders(req);
        JsonObject json = new JsonObject();
        json.putBoolean("websocket", websocket);
        json.putBoolean("cookie_needed", config.getBoolean("insert_JSESSIONID"));
        json.putArray("origins", new JsonArray().add("*:*"));
        // Java ints are signed, so we need to use a long and add the offset so
        // the result is not negative
        json.putNumber("entropy", RAND_OFFSET + new Random().nextInt());
        setCORS(req);
        req.response.end(json.encode());
      }
    };
  }

  static void setNoCacheHeaders(HttpServerRequest req) {
    req.response.headers().put("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
  }

  static Handler<HttpServerRequest> createCORSOptionsHandler(final JsonObject config, final String methods) {
    return new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("In CORS options handler");
        req.response.headers().put("Cache-Control", "public,max-age=31536000");
        long oneYearSeconds = 365 * 24 * 60 * 60;
        long oneYearms = oneYearSeconds * 1000;
        String expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(System.currentTimeMillis() + oneYearms));
        req.response.headers().put("Expires", expires);
        req.response.headers().put("Access-Control-Allow-Methods", methods);
        req.response.headers().put("Access-Control-Max-Age", String.valueOf(oneYearSeconds));
        setCORS(req);
        setJSESSIONID(config, req);
        req.response.statusCode = 204;
        req.response.end();
      }
    };
  }
}
