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
class HtmlFileTransport extends BaseTransport {

  private static final Logger log = Logger.getLogger(HtmlFileTransport.class);

  private static final String HTML_FILE_TEMPLATE;

  static {
    String str =
    "<!doctype html>\n" +
    "<html><head>\n" +
    "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
    "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
    "</head><body><h2>Don't panic!</h2>\n" +
    "  <script>\n" +
    "    document.domain = document.domain;\n" +
    "    var c = parent.{{ callback }};\n" +
    "    c.start();\n" +
    "    function p(d) {c.message(d);};\n" +
    "    window.onload = function() {c.stop();};\n" +
    "  </script>";

    String str2 = str.replace("{{ callback }}", "");
    StringBuilder sb = new StringBuilder(str);
    int extra = 1024 - str2.length();
    for (int i = 0; i < extra; i++) {
      sb.append(' ');
    }
    sb.append("\r\n");
    HTML_FILE_TEMPLATE = sb.toString();
  }

  HtmlFileTransport(Map<String, Session> sessions) {
    super(sessions);
  }

  void init(RouteMatcher rm, String basePath, final Handler<SockJSSocket> sockHandler) {
    String htmlFileRE = basePath + COMMON_PATH_ELEMENT_RE + "htmlfile";

    rm.getWithRegEx(htmlFileRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String sessionID = req.getParams().get("param0");
        Session session = sessions.get(sessionID);
        if (session == null) {
          req.response.setChunked(true);
          String cbParam = req.getParams().get("callback");
          if (cbParam == null) {
            cbParam = req.getParams().get("c");
            if (cbParam == null) {
              req.response.statusCode = 500;
              req.response.end("\"callback\" parameter required\n");
              return;
            }
          }
          String htmlFile = HTML_FILE_TEMPLATE.replace("{{ callback }}", cbParam);
          session = new Session();
          sessions.put(sessionID, session);
          sockHandler.handle(session);
          req.response.putHeader("Content-Type", "text/html; charset=UTF-8");
          req.response.putHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
          setCookies(req);
          req.response.write(htmlFile);
          req.response.write("<script>\np(\"o\");\n</script>\r\n");
          session.tcConn = new HtmlFileTcConn(req.response);
        } else {
          //TODO?
        }
      }
    });
  }

  class HtmlFileTcConn implements TransportConnection {

    final HttpServerResponse resp;

    HtmlFileTcConn(HttpServerResponse resp) {
      this.resp = resp;
    }

    public void write(Session session) {
      StringBuffer sb = new StringBuffer();
      sb.append("<script>\np(\"");
      sb.append("a[");
      int count = 0;
      int size = session.messages.size();
      for (String msg : session.messages) {
        sb.append("\\\"").append(msg).append("\\\"");
        if (++count != size) {
          sb.append(',');
        }
      }
      sb.append("]");
      sb.append("\");\n</script>\r\n");
      resp.write(sb.toString());
    }
  }
}
