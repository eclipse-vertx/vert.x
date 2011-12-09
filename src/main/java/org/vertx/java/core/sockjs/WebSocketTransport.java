package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
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

  WebSocketTransport(Map<String, Session> sessions) {
    super(sessions);
  }

  void init(WebSocketMatcher wsMatcher, RouteMatcher rm, String basePath, final Handler<SockJSSocket> sockHandler) {
    String wsRE = basePath + COMMON_PATH_ELEMENT_RE + "websocket";

    wsMatcher.addRegEx(wsRE, new Handler<WebSocketMatcher.Match>() {

      public void handle(final WebSocketMatcher.Match match) {
        final Session newSession = new Session();
        match.ws.writeTextFrame("o");
        sockHandler.handle(newSession);
        if (!newSession.closed) {
          match.ws.dataHandler(new Handler<Buffer>() {
            public void handle(Buffer data) {
              if (!newSession.closed) {
                String msgs = data.toString();
                if (msgs.equals("")) {
                  //Ignore empty frames
                } else if (msgs.startsWith("[\"") && msgs.endsWith("\"]")) {
                  newSession.handleMessages(parseMessageString(msgs));
                } else {
                  //Invalid JSON - we close the connection
                  match.ws.close();
                }
              }
            }
          });
          newSession.tcConn = new WebSocketTcConn(match.ws);
        } else {
          match.ws.writeTextFrame("c[3000,\"Go away!\"]");
          //TODO should we close the actual websocket??
        }
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

  class WebSocketTcConn implements TransportConnection {

    final WebSocket ws;

    WebSocketTcConn(WebSocket ws) {
      this.ws = ws;
    }

    public void write(Session session) {
      if (session.closed) {
        throw new IllegalStateException("Session is closed");
      }
      StringBuffer sb = new StringBuffer();
      sb.append("a[");
      int count = 0;
      int size = session.messages.size();
      for (String msg : session.messages) {
        sb.append("\"").append(msg).append("\"");
        if (++count != size) {
          sb.append(',');
        }
      }
      sb.append("]");
      ws.writeTextFrame(sb.toString());
    }
  }
}
