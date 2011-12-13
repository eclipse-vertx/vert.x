package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class JsonPTransport extends BaseTransport {

  private static final Logger log = Logger.getLogger(JsonPTransport.class);

  JsonPTransport(RouteMatcher rm, String basePath, final Map<String, Session> sessions, final AppConfig config,
            final Handler<SockJSSocket> sockHandler) {
    super(sessions, config);

    String jsonpRE = basePath + COMMON_PATH_ELEMENT_RE + "jsonp";

    rm.getWithRegEx(jsonpRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {

        String callback = req.getParams().get("callback");
        if (callback == null) {
          callback = req.getParams().get("c");
          if (callback == null) {
            req.response.statusCode = 500;
            req.response.end("\"callback\" parameter required\n");
            return;
          }
        }

        String sessionID = req.getParams().get("param0");
        Session session = getSession(config.getSessionTimeout(), config.getHeartbeatPeriod(), sessionID, sockHandler);
        session.register(new JsonPListener(req, session, callback));
      }
    });

    String jsonpSendRE = basePath + COMMON_PATH_ELEMENT_RE + "jsonp_send";

    rm.postWithRegEx(jsonpSendRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String sessionID = req.getParams().get("param0");
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

  private void handleSend(final HttpServerRequest req, final Session session) {
    req.bodyHandler(new Handler<Buffer>() {

      public void handle(Buffer buff) {
        String body = buff.toString();

        boolean urlEncoded;
        String ct = req.getHeader("Content-Type");
        if (ct.equalsIgnoreCase("application/x-www-form-urlencoded")) {
          urlEncoded = true;
        } else if (ct.equals("text/plain")) {
          urlEncoded = false;
        } else {
          req.response.statusCode = 500;
          req.response.end("Invalid Content-Type");
          return;
        }

        if (body.equals("") || urlEncoded && (!body.startsWith("d=") || body.length() <= 2)) {
          req.response.statusCode = 500;
          req.response.end("Payload expected.");
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

        //Sock-JS client will only ever send Strings in a JSON array so we can do some cheap parsing
        //without having to use a JSON lib

        if (!checkJSON(body, req.response)) {
          return;
        }

        String[] parts = parseMessageString(body);

        setJSESSIONID(config, req);
        req.response.end("ok");

        session.handleMessages(parts);
      }
    });
  }

  private class JsonPListener implements TransportListener {

    final HttpServerRequest req;
    final Session session;
    final String callback;
    boolean headersWritten;

    JsonPListener(HttpServerRequest req, Session session, String callback) {
      this.req = req;
      this.session = session;
      this.callback = callback;
    }

    public void sendFrame(String payload) {

      if (!headersWritten) {
        req.response.setChunked(true);
        req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");
        req.response.putHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        setJSESSIONID(config, req);
        headersWritten = true;
      }

      payload = payload.replace("\"", "\\\"");
      StringBuilder sb = new StringBuilder();
      sb.append(callback).append("(\"");
      sb.append(payload);
      sb.append("\");\r\n");

      //End the response and close the HTTP connection

      req.response.write(sb.toString());

      req.response.end(true);
      session.resetListener();
    }
  }
}
