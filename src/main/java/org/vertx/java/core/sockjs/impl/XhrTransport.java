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
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class XhrTransport extends BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(XhrTransport.class);

  private static final Buffer H_BLOCK;

  static {
    byte[] bytes = new byte[2048 + 1];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte)'h';
    }
    bytes[bytes.length - 1] = (byte)'\n';
    H_BLOCK = Buffer.create(bytes);
  }

  XhrTransport(RouteMatcher rm, String basePath, final Map<String, Session> sessions, final AppConfig config,
            final Handler<SockJSSocket> sockHandler) {

    super(sessions, config);

    String xhrBase = basePath + COMMON_PATH_ELEMENT_RE;
    String xhrRE = xhrBase + "xhr";
    String xhrStreamRE = xhrBase + "xhr_streaming";

    Handler<HttpServerRequest> xhrOptionsHandler = createCORSOptionsHandler(config, "OPTIONS, POST");

    rm.optionsWithRegEx(xhrRE, xhrOptionsHandler);
    rm.optionsWithRegEx(xhrStreamRE, xhrOptionsHandler);

    registerHandler(rm, sockHandler, xhrRE, false, config);
    registerHandler(rm, sockHandler, xhrStreamRE, true, config);

    String xhrSendRE = basePath + COMMON_PATH_ELEMENT_RE + "xhr_send";

    rm.optionsWithRegEx(xhrSendRE, xhrOptionsHandler);

    rm.postWithRegEx(xhrSendRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {

        String sessionID = req.getAllParams().get("param0");

        final Session session = sessions.get(sessionID);

        if (session != null) {
          handleSend(req, session);
        } else {
          req.response.statusCode = 404;
          setJSESSIONID(config, req);
          req.response.end();
        }
      }
    });
  }

  private void registerHandler(RouteMatcher rm, final Handler<SockJSSocket> sockHandler, String re,
                               final boolean streaming, final AppConfig config) {
    rm.postWithRegEx(re, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String sessionID = req.getAllParams().get("param0");
        Session session = getSession(config.getSessionTimeout(), config.getHeartbeatPeriod(), sessionID, sockHandler);

        session.register(streaming? new XhrStreamingListener(config.getMaxBytesStreaming(), req, session) : new XhrPollingListener(req, session));
      }
    });
  }

  private void handleSend(final HttpServerRequest req, final Session session) {

    req.bodyHandler(new Handler<Buffer>() {
      public void handle(Buffer buff) {
        String msgs = buff.toString();

        if (msgs.equals("")) {
          req.response.statusCode = 500;
          req.response.end("Payload expected.");
          return;
        }

        if (!session.handleMessages(msgs)) {
          sendInvalidJSON(req.response);
        } else {
          req.response.putHeader("Content-Type", "text/plain");
          setJSESSIONID(config, req);
          setCORS(req);
          req.response.statusCode = 204;
          req.response.end();
        }
      }
    });
  }

  private abstract class BaseXhrListener implements TransportListener {
    final HttpServerRequest req;
    final Session session;

    boolean headersWritten;

    BaseXhrListener(HttpServerRequest req, Session session) {
      this.req = req;
      this.session = session;
    }

    public void sendFrame(String body) {
      if (!headersWritten) {
        req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");
        setJSESSIONID(config, req);
        setCORS(req);
        req.response.setChunked(true);
        headersWritten = true;
      }
    }
  }

  private class XhrPollingListener extends BaseXhrListener {

    XhrPollingListener(HttpServerRequest req, Session session) {
      super(req, session);
    }

    public void sendFrame(String body) {
      super.sendFrame(body);
      req.response.end(body + "\n", true);
      session.resetListener();
    }
  }

  private class XhrStreamingListener extends BaseXhrListener {

    int bytesSent;
    int maxBytesStreaming;

    XhrStreamingListener(int maxBytesStreaming, HttpServerRequest req, Session session) {
      super(req, session);
      this.maxBytesStreaming = maxBytesStreaming;
    }

    public void sendFrame(String body) {
      boolean hr = headersWritten;
      super.sendFrame(body);
      if (!hr) {
        req.response.write(H_BLOCK);
      }
      String sbody = body + "\n";
      Buffer buff = Buffer.create(sbody);
      req.response.write(buff);
      bytesSent += buff.length();
      if (bytesSent >= maxBytesStreaming) {
        // Reset and close the connection
        session.resetListener();
        req.response.end(true);
      }
    }
  }
}
