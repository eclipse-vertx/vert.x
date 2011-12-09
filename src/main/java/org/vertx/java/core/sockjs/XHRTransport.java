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

    String xhrBase = basePath + COMMON_PATH_ELEMENT_RE;
    String xhrRE = xhrBase + "xhr";
    String xhrStreamRE = xhrBase + "xhr_streaming";

    Handler<HttpServerRequest> xhrOptionsHandler = createCORSOptionsHandler("OPTIONS, POST");

    rm.optionsWithRegEx(xhrRE, xhrOptionsHandler);
    rm.optionsWithRegEx(xhrStreamRE, xhrOptionsHandler);

    class XhrHandler implements Handler<HttpServerRequest> {

      boolean streaming;

      XhrHandler(boolean streaming) {
        this.streaming = streaming;
      }

      public void handle(HttpServerRequest req) {
        String sessionID = req.getParams().get("param0");
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
            if (session.closed) {
              req.response.end("c[3000,\"Go away!\"]\n");
            }
          } else {
            req.response.end("o\n");
          }
        } else {
          req.response.setChunked(true);
          if (session.tcConn != null) {
            //Can't have more than one request waiting
            if (streaming) {
              req.response.write(H_BLOCK);
              req.response.end("c[3000,\"Go away!\"]\n");
            } else {
              req.response.end("c[2010,\"Another connection still open\"]\n");
            }
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

    String xhrSendRE = basePath + COMMON_PATH_ELEMENT_RE + "xhr_send";

    rm.optionsWithRegEx(xhrSendRE, xhrOptionsHandler);

    rm.postWithRegEx(xhrSendRE, new Handler<HttpServerRequest>() {
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
        String msgs = buff.toString();


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

        String[] parts = parseMessageString(msgs);

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
