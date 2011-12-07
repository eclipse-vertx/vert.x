package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class XHRTransport extends BaseTransport {

  //TODO xhr timeout after seconds if not have "receiving connection"

  //TODO xhr streaming - close request if more than 128K sent


  private static final Logger log = Logger.getLogger(XHRTransport.class);

  private static final Buffer H_BLOCK;

  static {
    byte[] bytes = new byte[2048 + 1];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte)'h';
    }
    bytes[bytes.length - 1] = (byte)'\n';
    H_BLOCK = Buffer.create(bytes);
  }

  XHRTransport(Map<String, Session> sessions) {
    super(sessions);
  }

  void init(RouteMatcher rm, String basePath, final Handler<SockJSSocket> sockHandler) {

    String xhrBase = basePath + COMMON_PATH_ELEMENT;
    String xhrRE = xhrBase + "xhr";
    String xhrStreamRE = xhrBase + "xhr_streaming";

    Handler<HttpServerRequest> xhrOptionsHandler = new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.putHeader("Cache-Control", "public,max-age=31536000");
        long oneYearSeconds = 365 * 24 * 60 * 60;
        long oneYearms = oneYearSeconds * 1000;
        String expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(System.currentTimeMillis() + oneYearms));
        req.response.putHeader("Expires", expires);
        req.response.putHeader("Allow", "OPTIONS, POST");
        req.response.putHeader("Access-Control-Max-Age", String.valueOf(oneYearSeconds));
        String origin = req.getHeader("Origin");
        if (origin == null) {
          origin = "*";
        }
        setCORS(req.response, origin);
        setCookies(req);
        req.response.statusCode = 204;
        req.response.end();
      }
    };

    rm.optionsWithRegEx(xhrRE, xhrOptionsHandler);
    rm.optionsWithRegEx(xhrStreamRE, xhrOptionsHandler);

    class XhrHandler implements Handler<HttpServerRequest> {

      boolean streaming;

      XhrHandler(boolean streaming) {
        this.streaming = streaming;
      }

      public void handle(HttpServerRequest req) {
        String sessionID = req.getParams().get("param1");
        Session session = sessions.get(sessionID);
        if (session == null) {
          session = new Session();
          sessions.put(sessionID, session);
          sockHandler.handle(session);
          req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");
          setCookies(req);
          setCORS(req.response, "*");
          req.response.setChunked(true);
          if (streaming) {
            req.response.write(H_BLOCK);
            req.response.write("o\n");
            session.tcConn = new XHRStreamingTCConn(req.response);
          } else {
            req.response.end("o\n");
          }
        } else {
          log.info("existing session");
          req.response.setChunked(true);
          if (session.tcConn != null) {
            //Can't have more than one request waiting
            req.response.end("c[2010,\"Another connection still open\"]\n");
          } else {
            if (!session.closed) {
              session.tcConn = streaming? new XHRStreamingTCConn(req.response) : new XHRPollingTCConn(req.response);
              if (!session.messages.isEmpty()) {
                session.writePendingMessagesToPollResponse();
              }
            } else {
              req.response.end("c[3000,\"Go away!\"]\n");
            }
          }
        }
      }
    }

    rm.postWithRegEx(xhrRE, new XhrHandler(false));

    rm.postWithRegEx(xhrStreamRE, new XhrHandler(true));

    String xhrSendRE = basePath + "\\/([^\\/\\.]+)\\/([^\\/\\.]+)\\/xhr_send";

    rm.optionsWithRegEx(xhrSendRE, xhrOptionsHandler);

    rm.postWithRegEx(xhrSendRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String sessionID = req.getParams().get("param1");
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
        String msgs = buff.toString();

        //Sock-JS client will never only ever send Strings in a JSON array so we can do some cheap parsing
        //without having to use a JSON lib

        if (msgs.equals("")) {
          req.response.statusCode = 500;
          req.response.end("Payload expected.");
          return;
        }

        //TODO can be optimised
        if (!(msgs.startsWith("[\"") && msgs.endsWith("\"]"))) {
          //Invalid
          req.response.statusCode = 500;
          req.response.end("Broken JSON encoding.");
          return;
        }

        String[] split = msgs.split("\"");
        String[] parts = new String[(split.length - 1) / 2];
        for (int i = 1; i < split.length - 1; i += 2) {
          parts[(i - 1) / 2] = split[i];
        }

        req.response.putHeader("Content-Type", "text/plain");
        setCookies(req);
        setCORS(req.response, "*");
        req.response.statusCode = 204;
        req.response.end();

        session.handleMessages(parts);
      }
    });
  }

  abstract class BaseXHRConn implements TransportConnection {
    final HttpServerResponse resp;

    BaseXHRConn(HttpServerResponse resp) {
      this.resp = resp;
    }

    StringBuffer writeMessages(Session session) {
      StringBuffer sb = new StringBuffer();
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
      sb.append("\n");
      return sb;
    }
  }

  class XHRPollingTCConn extends BaseXHRConn {

    XHRPollingTCConn(HttpServerResponse resp) {
      super(resp);
    }

    public void write(Session session) {
      StringBuffer sb = writeMessages(session);
      //End the response and close the HTTP connection
      resp.end(sb.toString(), true);
      session.tcConn = null;
    }
  }

  class XHRStreamingTCConn extends BaseXHRConn {

    XHRStreamingTCConn(HttpServerResponse resp) {
      super(resp);
    }

    public void write(Session session) {
      StringBuffer sb = writeMessages(session);
      resp.write(sb.toString());
    }
  }
}
