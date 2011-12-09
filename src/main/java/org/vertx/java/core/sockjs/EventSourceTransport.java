package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class EventSourceTransport extends BaseTransport {

  private static final Logger log = Logger.getLogger(EventSourceTransport.class);

  EventSourceTransport(Map<String, Session> sessions) {
    super(sessions);
  }

  void init(RouteMatcher rm, String basePath, final Handler<SockJSSocket> sockHandler) {
    String eventSourceRE = basePath + COMMON_PATH_ELEMENT_RE + "eventsource";

    rm.getWithRegEx(eventSourceRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String sessionID = req.getParams().get("param0");
        Session session = sessions.get(sessionID);
        if (session == null) {
          session = new Session();
          sessions.put(sessionID, session);
          sockHandler.handle(session);
          req.response.putHeader("Content-Type", "text/event-stream; charset=UTF-8");
          req.response.putHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
          setCookies(req);
          req.response.setChunked(true);
          req.response.write("\r\n");;
          req.response.write("data: o\r\n\r\n");
          session.tcConn = new EventSourceTcConn(req.response);
        } else {
          //TODO??
        }
      }
    });
  }

  class EventSourceTcConn implements TransportConnection {

    final HttpServerResponse resp;

    EventSourceTcConn(HttpServerResponse resp) {
      this.resp = resp;
    }

    public void write(Session session) {
      StringBuffer sb = new StringBuffer();
      sb.append("data: ");
      sb.append("a[");
      int count = 0;
      int size = session.messages.size();
      for (String msg : session.messages) {
        sb.append('"').append(msg).append('"');
        if (++count != size) {
          sb.append(',');
        }
      }
      sb.append("]");
      sb.append("\r\n\r\n");
      resp.write(sb.toString());
    }
  }
}
