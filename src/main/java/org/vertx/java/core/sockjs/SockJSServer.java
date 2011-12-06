package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSServer {

  private static final Logger log = Logger.getLogger(SockJSServer.class);

  private static final String DEFAULT_SOCK_JS_URL = "http://cdn.sockjs.org/sockjs-0.1.min.js";

  private final String iframeHTML;

  private class AppInfo {
    final String appName;
    final String basePath;

    private AppInfo(String appName, String basePath) {
      this.appName = appName;
      this.basePath = basePath;
    }
  }

  private RouteMatcher rm = new RouteMatcher();

  public void installApp(final String appName, String basePath) {

    if (basePath == null || basePath.equals("") || basePath.endsWith("/")) {
      throw new IllegalArgumentException("Invalid base path: " + basePath);
    }

    // Base handler for app

    rm.getWithRegEx(basePath + "\\/?", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.putHeader("Content-Type", "text/plain; charset=UTF-8");
        req.response.end("Welcome to SockJS!\n");
      }
    });

    // Iframe handlers

    Handler<HttpServerRequest> iframeHandler = new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        try {
          String etag = getMD5String();
          if (etag.equals(req.getHeader("if-none-match"))) {
            req.response.statusCode = 304;
            req.response.end();
          } else {
            log.info("serving iframe.html, path: " + req.path);
            req.response.putHeader("Content-Type", "text/html; charset=UTF-8");
            req.response.putHeader("Cache-Control", "public,max-age=31536000");
            long oneYear = 365 * 24 * 60 * 60 * 1000;
            String expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(System.currentTimeMillis() + oneYear));
            req.response.putHeader("Expires", expires);
            req.response.putHeader("ETag", etag);
            req.response.end(iframeHTML);
          }
        } catch (Exception e) {
          log.error("Failed to server iframe", e);
        }
      }
    };

    // The following regex can probably be combined into one, but my neckbeard is not bushy enough for that

    // Request exactly for iframe.html
    rm.getWithRegEx(basePath + "\\/iframe\\.html", iframeHandler);

    // Versioned
    rm.getWithRegEx(basePath + "\\/iframe-[^\\/]*\\.html", iframeHandler);

    // With arbitrary query string on the end
    rm.getWithRegEx(basePath + "\\/iframe-[^\\/]*\\.html\\?.*", iframeHandler);


    class XHRPollingSession {
      final Queue<String> messages = new LinkedList<>();
      HttpServerResponse currentResp;
    }

    //TODO use shared set for this so scales better
    final Map<String, XHRPollingSession> sessions = new HashMap<>();

    rm.postWithRegEx(basePath + "\\/([^\\/\\.]+)\\/([^\\/\\.]+)\\/xhr", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        String serverID = req.getParams().get("param0");
        String sessionID = req.getParams().get("param1");
        log.info("xhr post, serverID is " + serverID + " sessonID is " + sessionID);
        final XHRPollingSession session = sessions.get(sessionID);
        if (session == null) {
          log.info("new session");
          sessions.put(sessionID, new XHRPollingSession());
          req.response.end("o\n");
        } else {
          log.info("existing session");
          if (session.currentResp != null) {
            //Can't have more than one request waiting
            req.response.end("c[2010,\"Another connection still open\"]");
          } else {
            //session.currentResp = req.response;
            sendMessagesToResponse(req.response, session.messages);
          }
        }
      }
    });

    //TODO timeout after seconds if not have "receiving connection"

    //TODO heartbeat

    rm.postWithRegEx(basePath + "\\/([^\\/\\.]+)\\/([^\\/\\.]+)\\/xhr_send", new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String serverID = req.getParams().get("param0");
        String sessionID = req.getParams().get("param1");
        log.info("xhr_send post, serverID is " + serverID + " sessonID is " + sessionID);
        final XHRPollingSession session = sessions.get(sessionID);
        if (session != null) {
          req.bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buff) {
              String msg = buff.toString();
              log.info("Got msg on xhr_send:" + msg);
              session.messages.add("a");
              req.response.statusCode = 204;
              req.response.end();
              if (session.currentResp != null) {
                sendMessagesToResponse(req.response, session.messages);
                session.currentResp = null;
              }
            }
          });
        } else {
          log.info("Unknown session, sending 404");
          req.response.statusCode = 404;
          //Preserve cookies
          String cookie = req.getHeader("Cookie");
          if (cookie != null) {
            req.response.putHeader("Set-Cookie", cookie);
          }
          req.response.end();
        }

      }
    });

    // Catch all for any other requests on this app
    rm.getWithRegEx(basePath + "\\/.+", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.statusCode = 404;
        req.response.end();
      }
    });
  }

  private void sendMessagesToResponse(HttpServerResponse response, Queue<String> messages) {
    response.setChunked(false);
    StringBuffer resp = new StringBuffer();
    resp.append("a[");
    for (String msg : messages) {
      resp.append("\"").append(msg).append("\"");
    }
    resp.append("]\n");
    response.end(resp.toString(), true);
    messages.clear();
  }

  private String getMD5String() throws Exception {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] bytes = md.digest(iframeHTML.getBytes("UTF-8"));
    StringBuffer sb = new StringBuffer();
    for (byte b: bytes) {
      sb.append(Integer.toHexString(b + 127));
    }
    return sb.toString();
  }

  public static void main(String[] args) throws Exception {
    log.info("Starting sock-js server");

    VertxInternal.instance.go(new Runnable() {
      public void run() {
        SockJSServer server = new SockJSServer();
        server.installApp("echo", "/echo");
        server.installApp("close", "/close");
        server.installApp("disabled_websocket_echo", "/disabled_websocket_echo");
        server.start();
      }
    });

    Thread.sleep(1000000);
  }

  public SockJSServer() {
    iframeHTML = IFRAME_TEMPLATE.replace("{{ sockjs_url }}", DEFAULT_SOCK_JS_URL);
  }

  public void start() {
    HttpServer server = new HttpServer();
    setHTTPHandler(server);
    server.websocketHandler(new WSHandler());
    server.listen(8080);
  }

  private void setHTTPHandler(HttpServer server) {

    // Catch all - serve a 404
    rm.get(".*", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        log.info("Main catch all matched " + req.path);
        req.response.statusCode = 404;
        req.response.end();
      }
    });

    server.requestHandler(rm);
  }

  class WSHandler implements Handler<WebSocket> {

    public void handle(WebSocket ws) {
      handleWebSocketConnect(ws);
    }
  }

  private void handleWebSocketConnect(WebSocket ws) {

  }

  private static final String IFRAME_TEMPLATE =
    "<!DOCTYPE html>\n" +
    "<html>\n" +
    "<head>\n" +
    "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
    "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
    "  <script>\n" +
    "    document.domain = document.domain;\n" +
    "    _sockjs_onload = function(){SockJS.bootstrap_iframe();};\n" +
    "  </script>\n" +
    "  <script src=\"{{ sockjs_url }}\"></script>\n" +
    "</head>\n" +
    "<body>\n" +
    "  <h2>Don't panic!</h2>\n" +
    "  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>\n" +
    "</body>\n" +
    "</html>";


}

