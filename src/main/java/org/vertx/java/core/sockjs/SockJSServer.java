package org.vertx.java.core.sockjs;

import org.codehaus.jackson.map.ObjectMapper;
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

  private RouteMatcher rm = new RouteMatcher();

  //TODO use shared set for this so scales better
  private final Map<String, XHRPollingSocket> xhrPollingSockets = new HashMap<>();

  private class XHRPollingSocket implements SockJSSocket {
    final Queue<String> messages = new LinkedList<>();
    HttpServerResponse currentResp;
    Handler<Buffer> dataHandler;
    boolean closed;

    public void write(Buffer buffer) {
      messages.add(buffer.toString());
    }

    public void dataHandler(Handler<Buffer> handler) {
      this.dataHandler = handler;
    }

    public void close() {
      closed = true;
    }

    void handlePollRequest(HttpServerRequest req) {
      if (currentResp != null) {
        //Can't have more than one request waiting
        req.response.end("c[2010,\"Another connection still open\"]\n");
      } else {
        if (!closed) {
          currentResp = req.response;
          if (!messages.isEmpty()) {
            writePendingMessagesToPollResponse(req.response, messages);
            currentResp = null;
          }
        } else {
          log.info("Closed so sending back go away");
          req.response.end("c[3000,\"Go away!\"]\n");
        }
      }
    }

    void handleSend(final HttpServerRequest req) {
      req.bodyHandler(new Handler<Buffer>() {
        public void handle(Buffer buff) {
          String msgs = buff.toString();
          log.info("Got msg on xhr_send:" + msgs);

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

          handleMessages(parts);
        }
      });
    }

    void handleMessages(String[] messages) {
      if (dataHandler != null) {
        for (String msg: messages) {
          dataHandler.handle(Buffer.create(msg));
        }
      }
    }

    void writePendingMessagesToPollResponse(HttpServerResponse response, Queue<String> messages) {
      response.setChunked(false);
      StringBuffer resp = new StringBuffer();
      resp.append("a[");
      int count = 0;
      int size = messages.size();
      for (String msg : messages) {
        resp.append('"').append(msg).append('"');
        if (++count != size) {
          resp.append(',');
        }
      }
      resp.append("]\n");
      response.end(resp.toString(), true);
      messages.clear();
    }
  }

  private void setCookies(HttpServerRequest req) {
    String cookies = req.getHeader("Cookie");
    String jsessionID = "dummy";
    //Preserve existing JSESSIONID, if any
    if (cookies != null) {
      String[] parts;
      if (cookies.contains(";")) {
        parts = cookies.split(";");
      } else {
        parts = new String[] {cookies};
      }
      for (String part: parts) {
        if (part.startsWith("JSESSIONID")) {
          jsessionID = part.substring(11);
          break;
        }
      }
    }
    req.response.putHeader("Set-Cookie", "JSESSIONID=" + jsessionID + ";path=/");
  }

  private void setCORS(HttpServerResponse resp, String origin) {
    resp.putHeader("Access-Control-Allow-Origin", origin);
    resp.putHeader("Access-Control-Allow-Credentials", "true");
  }

  public void installApp(final String appName, final boolean websocketsEnabled, String basePath,
                         final Handler<SockJSSocket> sockHandler) {

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

    Handler<HttpServerRequest> iframeHandler = createIFrameHandler();

    // The following regex can probably be combined into one, but my neckbeard is not bushy enough for that

    // Request exactly for iframe.html
    rm.getWithRegEx(basePath + "\\/iframe\\.html", iframeHandler);

    // Versioned
    rm.getWithRegEx(basePath + "\\/iframe-[^\\/]*\\.html", iframeHandler);

    // With arbitrary query string on the end
    rm.getWithRegEx(basePath + "\\/iframe-[^\\/]*\\.html\\?.*", iframeHandler);

    // xhr-polling

    String xhrRE = basePath + "\\/([^\\/\\.]+)\\/([^\\/\\.]+)\\/xhr";

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
        // CORS shit
        setCORS(req.response, origin);

        // Cookies shit
        setCookies(req);

        req.response.statusCode = 204;
        req.response.end();
      }
    };

    rm.optionsWithRegEx(xhrRE, xhrOptionsHandler);

    rm.postWithRegEx(xhrRE, new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        String serverID = req.getParams().get("param0");
        String sessionID = req.getParams().get("param1");
        log.info("xhr post, serverID is " + serverID + " sessonID is " + sessionID);
        XHRPollingSocket session = xhrPollingSockets.get(sessionID);
        if (session == null) {
          log.info("new session");
          session = new XHRPollingSocket();
          xhrPollingSockets.put(sessionID, session);
          sockHandler.handle(session);
          req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");
          setCookies(req);
          setCORS(req.response, "*");
          req.response.end("o\n");
        } else {
          log.info("existing session");
          session.handlePollRequest(req);
        }
      }
    });

    //TODO timeout after seconds if not have "receiving connection"

    //TODO heartbeat

    String xhrSendRE = basePath + "\\/([^\\/\\.]+)\\/([^\\/\\.]+)\\/xhr_send";

    rm.optionsWithRegEx(xhrSendRE, xhrOptionsHandler);

    rm.postWithRegEx(xhrSendRE, new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        String serverID = req.getParams().get("param0");
        String sessionID = req.getParams().get("param1");
        log.info("xhr_send post, serverID is " + serverID + " sessonID is " + sessionID);
        final XHRPollingSocket session = xhrPollingSockets.get(sessionID);
        if (session != null) {
          session.handleSend(req);
        } else {
          log.info("Unknown session, sending 404");
          req.response.statusCode = 404;
          setCookies(req);
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

  private Handler<HttpServerRequest> createIFrameHandler() {
    return new Handler<HttpServerRequest>() {
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
        server.installApp("echo", true, "/echo", new Handler<SockJSSocket>() {
          public void handle(final SockJSSocket sock) {
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer buff) {
                sock.write(buff);
              }
            });
          }
        });
        server.installApp("close", true, "/close", new Handler<SockJSSocket>() {
          public void handle(final SockJSSocket sock) {
            log.info("Socket openened in close app, closing it immediately");
            sock.close();
          }
        });
        server.installApp("disabled_websocket_echo", true, "/disabled_websocket_echo", new Handler<SockJSSocket>() {
          public void handle(final SockJSSocket sock) {
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer buff) {
                sock.write(buff);
              }
            });
          }
        });
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

  private interface SockJSSocket {

    void write(Buffer buffer);

    void dataHandler(Handler<Buffer> handler);

    void close();
  }



}

