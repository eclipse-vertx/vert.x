package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JSONPTransport extends BaseTransport {

  private static final Logger log = Logger.getLogger(JSONPTransport.class);

  JSONPTransport(Map<String, Session> sessions) {
    super(sessions);
  }

  void init(RouteMatcher rm, String basePath, final Handler<SockJSSocket> sockHandler) {
    String jsonpRE = basePath + COMMON_PATH_ELEMENT_RE + "jsonp";

    rm.getWithRegEx(jsonpRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String cbParam = req.getParams().get("callback");
        if (cbParam == null) {
          cbParam = req.getParams().get("c");
          if (cbParam == null) {
            req.response.statusCode = 500;
            req.response.end("\"callback\" parameter required\n");
            return;
          }
        }
        String sessionID = req.getParams().get("param0");
        Session session = sessions.get(sessionID);
        if (session == null) {
          req.response.setChunked(true);

          session = new Session();
          sessions.put(sessionID, session);
          sockHandler.handle(session);
          req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");
          req.response.putHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
          setCookies(req);
          req.response.write(cbParam + "(\"o\");\r\n");
        } else {
          req.response.setChunked(true);
          if (session.tcConn != null) {
            //Can't have more than one request waiting
            req.response.end("c[2010,\"Another connection still open\"]\n");
          } else {
            if (!session.closed) {
              session.tcConn = new JSONPTcConn(req.response, cbParam);
              if (!session.messages.isEmpty()) {
                session.writePendingMessagesToPollResponse();
              }
            } else {
              req.response.end("c[3000,\"Go away!\"]\n");
            }
          }
        }
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
          setCookies(req);
          req.response.end();
        }
      }
    });
  }

  private void handleSend(final HttpServerRequest req, final Session session) {
    req.response.setChunked(true);

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

        //TODO can be optimised
        if (!(body.startsWith("[\"") && body.endsWith("\"]"))) {
          //Invalid
          req.response.statusCode = 500;
          req.response.end("Broken JSON encoding.");
          return;
        }

        String[] split = body.split("\"");
        String[] parts = new String[(split.length - 1) / 2];
        for (int i = 1; i < split.length - 1; i += 2) {
          parts[(i - 1) / 2] = split[i];
        }

        setCookies(req);
        req.response.end("ok");

        session.handleMessages(parts);
      }
    });
  }

  class JSONPTcConn implements TransportConnection {

    final HttpServerResponse resp;
    final String callback;

    JSONPTcConn(HttpServerResponse resp, String callback) {
      this.resp = resp;
      this.callback = callback;
    }

    public void write(Session session) {
      StringBuffer sb = new StringBuffer();
      sb.append(callback).append("(\"a[");
      int count = 0;
      int size = session.messages.size();
      for (String msg : session.messages) {
        sb.append("\\\"").append(msg).append("\\\"");
        if (++count != size) {
          sb.append(',');
        }
      }
      sb.append("]\");\r\n");

      //End the response and close the HTTP connection

      resp.write(sb.toString());

      resp.end(true);
      session.tcConn = null;
    }
  }
}
