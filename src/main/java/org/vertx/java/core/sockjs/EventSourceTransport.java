package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
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
          session = new Session(sockHandler);
          sessions.put(sessionID, session);
        }
        session.register(new EventSourceListener(req));
      }
    });
  }

  private class EventSourceListener implements TransportListener {

    final HttpServerRequest req;

    boolean headersWritten;

    EventSourceListener(HttpServerRequest req) {
      this.req = req;
    }

    public void sendFrame(StringBuffer payload) {
      if (!headersWritten) {
        req.response.putHeader("Content-Type", "text/event-stream; charset=UTF-8");
        req.response.putHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        setCookies(req);
        req.response.setChunked(true);
        req.response.write("\r\n");
        headersWritten = true;
      }
      StringBuffer sb = new StringBuffer();
      sb.append("data: ");
      sb.append(payload);
      sb.append("\r\n\r\n");
      req.response.write(sb.toString());
    }

  }
}
