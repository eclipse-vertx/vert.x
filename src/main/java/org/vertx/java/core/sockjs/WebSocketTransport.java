package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketMatcher;
import org.vertx.java.core.logging.Logger;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class WebSocketTransport extends BaseTransport {

  private static final Logger log = Logger.getLogger(WebSocketTransport.class);

  WebSocketTransport(WebSocketMatcher wsMatcher, RouteMatcher rm, String basePath, Map<String, Session> sessions,
                     final AppConfig config,
            final Handler<SockJSSocket> sockHandler) {
    super(sessions, config);
    String wsRE = basePath + COMMON_PATH_ELEMENT_RE + "websocket";

    wsMatcher.addRegEx(wsRE, new Handler<WebSocketMatcher.Match>() {

      public void handle(final WebSocketMatcher.Match match) {

        final Session session = new Session(config.getHeartbeatPeriod(), sockHandler);
        session.register(new WebSocketListener(match.ws, session));

        match.ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            if (!session.isClosed()) {
              String msgs = data.toString();
              if (msgs.equals("")) {
                //Ignore empty frames
              } else if (msgs.startsWith("[\"") && msgs.endsWith("\"]")) {
                session.handleMessages(parseMessageString(msgs));
              } else {
                //Invalid JSON - we close the connection
                match.ws.close();
              }
            }
          }
        });
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
        request.response.statusCode = 405;
        request.response.end();
      }
    });
  }

  private static class WebSocketListener implements TransportListener {

    final WebSocket ws;
    final Session session;

    WebSocketListener(WebSocket ws, Session session) {
      this.ws = ws;
      this.session = session;
    }

    public void sendFrame(final String payload) {
      ws.writeTextFrame(payload);
    }
  }
}
