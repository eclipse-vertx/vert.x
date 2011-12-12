package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.logging.Logger;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class XhrTransport extends BaseTransport {

  //TODO xhr timeout after seconds if not have "receiving connection"

  //TODO xhr streaming - close request if more than 128K sent

  private static final Logger log = Logger.getLogger(XhrTransport.class);

  private static final Buffer H_BLOCK;

  static {
    byte[] bytes = new byte[2048 + 1];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte)'h';
    }
    bytes[bytes.length - 1] = (byte)'\n';
    H_BLOCK = Buffer.create(bytes);
  }

  XhrTransport(Map<String, Session> sessions) {
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
          session = new Session(sockHandler);
          sessions.put(sessionID, session);
        }
        log.info("Registering xhr for session " + sessionID);
        session.register(streaming? new XhrStreamingListener(req, session) : new XhrPollingListener(req, session));
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
        if (!checkJSON(msgs, req.response)) {
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

  private abstract class BaseXhrListener implements TransportListener {
    final HttpServerRequest req;
    final Session session;

    boolean headersWritten;

    BaseXhrListener(HttpServerRequest req, Session session) {
      this.req = req;
      this.session = session;
    }

    public void sendFrame(StringBuffer payload) {
      if (!headersWritten) {
        req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");
        setCookies(req);
        setCORS(req.response, "*");
        req.response.setChunked(true);
        headersWritten = true;
      }
    }
  }

  private class XhrPollingListener extends BaseXhrListener {

    XhrPollingListener(HttpServerRequest req, Session session) {
      super(req, session);
    }

    public void sendFrame(StringBuffer payload) {
      super.sendFrame(payload);
      req.response.end(payload.append("\n").toString(), true);
      session.resetListener();
    }
  }

  private class XhrStreamingListener extends BaseXhrListener {

    XhrStreamingListener(HttpServerRequest req, Session session) {
      super(req, session);
    }

    public void sendFrame(StringBuffer payload) {
      boolean hr = headersWritten;
      super.sendFrame(payload);
      if (!hr) {
        req.response.write(H_BLOCK);
      }
      req.response.write(payload.append("\n").toString());
    }
  }
}
