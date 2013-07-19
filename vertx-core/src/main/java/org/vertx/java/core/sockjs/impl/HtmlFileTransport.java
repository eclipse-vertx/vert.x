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

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HtmlFileTransport extends BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(HtmlFileTransport.class);

  private static final String HTML_FILE_TEMPLATE;

  static {
    String str =
    "<!doctype html>\n" +
    "<html><head>\n" +
    "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
    "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
    "</head><body><h2>Don't panic!</h2>\n" +
    "  <script>\n" +
    "    document.domain = document.domain;\n" +
    "    var c = parent.{{ callback }};\n" +
    "    c.start();\n" +
    "    function p(d) {c.message(d);};\n" +
    "    window.onload = function() {c.stop();};\n" +
    "  </script>";

    String str2 = str.replace("{{ callback }}", "");
    StringBuilder sb = new StringBuilder(str);
    int extra = 1024 - str2.length();
    for (int i = 0; i < extra; i++) {
      sb.append(' ');
    }
    sb.append("\r\n");
    HTML_FILE_TEMPLATE = sb.toString();
  }

  HtmlFileTransport(VertxInternal vertx, RouteMatcher rm, String basePath, Map<String, Session> sessions, final JsonObject config,
            final Handler<SockJSSocket> sockHandler) {
    super(vertx, sessions, config);
    String htmlFileRE = basePath + COMMON_PATH_ELEMENT_RE + "htmlfile";

    rm.getWithRegEx(htmlFileRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("HtmlFile, get: " + req.uri());
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
        session.register(new HtmlFileListener(config.getInteger("max_bytes_streaming"), req, callback, session));
      }
    });
  }

  private class HtmlFileListener extends BaseListener {

    final int maxBytesStreaming;
    final HttpServerRequest req;
    final String callback;
    final Session session;
    boolean headersWritten;
    int bytesSent;
    boolean closed;

    HtmlFileListener(int maxBytesStreaming, HttpServerRequest req, String callback, Session session) {
      this.maxBytesStreaming = maxBytesStreaming;
      this.req = req;
      this.callback = callback;
      this.session = session;
      addCloseHandler(req.response(), session);
    }

    public void sendFrame(String body) {
      if (log.isTraceEnabled()) log.trace("HtmlFile, sending frame");
      if (!headersWritten) {
        String htmlFile = HTML_FILE_TEMPLATE.replace("{{ callback }}", callback);
        req.response().headers().set("Content-Type", "text/html; charset=UTF-8");
        setNoCacheHeaders(req);
        req.response().setChunked(true);
        setJSESSIONID(config, req);
        req.response().write(htmlFile);
        headersWritten = true;
      }
      body = escapeForJavaScript(body);
      StringBuilder sb = new StringBuilder();
      sb.append("<script>\np(\"");
      sb.append(body);
      sb.append("\");\n</script>\r\n");
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
          closed = true;
        } catch (IllegalStateException e) {
          // Underlying connection might already be closed - that's fine
        }
      }
    }
  }
}
