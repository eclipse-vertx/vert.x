package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketMatcher;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSServer {

  private static final Logger log = Logger.getLogger(SockJSServer.class);

  private static final String DEFAULT_SOCK_JS_URL = "http://cdn.sockjs.org/sockjs-0.1.min.js";

  //TODO heartbeat

  private final String iframeHTML;

  private RouteMatcher rm = new RouteMatcher();

  private WebSocketMatcher wsMatcher = new WebSocketMatcher();

  //TODO use shared set for this so scales better
  private final Map<String, Session> sessions = new HashMap<>();

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

    // Request exactly for iframe.html
    rm.getWithRegEx(basePath + "\\/iframe\\.html", iframeHandler);

    // Versioned
    rm.getWithRegEx(basePath + "\\/iframe-[^\\/]*\\.html", iframeHandler);

    // Chunking test
    rm.postWithRegEx(basePath + "\\/chunking_test", createChunkingTestHandler());
    rm.optionsWithRegEx(basePath + "\\/chunking_test", BaseTransport.createCORSOptionsHandler("OPTIONS, POST"));

    // Transports

    new XHRTransport(sessions).init(rm, basePath, sockHandler);
    new EventSourceTransport(sessions).init(rm, basePath, sockHandler);
    new HtmlFileTransport(sessions).init(rm, basePath, sockHandler);
    new JSONPTransport(sessions).init(rm, basePath, sockHandler);

    if (websocketsEnabled) {
      new WebSocketTransport(sessions).init(wsMatcher, rm, basePath, sockHandler);
    }

    // Catch all for any other requests on this app

    rm.getWithRegEx(basePath + "\\/.+", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.statusCode = 404;
        req.response.end();
      }
    });

  }

  private Handler<HttpServerRequest> createChunkingTestHandler() {
    return new Handler<HttpServerRequest>() {

      class TimeoutInfo {
        final long timeout;
        final Buffer buff;

        TimeoutInfo(long timeout, Buffer buff) {
          this.timeout = timeout;
          this.buff = buff;
        }
      }

      List<TimeoutInfo> timeouts = new ArrayList<>();

      private void setTimeout(long delay, final Buffer buff) {
        timeouts.add(new TimeoutInfo(delay, buff));
      }

      private void runTimeouts(HttpServerResponse response) {
        final Iterator<TimeoutInfo> iter = timeouts.iterator();
        nextTimeout(iter, response);
      }

      private void nextTimeout(final Iterator<TimeoutInfo> iter, final HttpServerResponse response) {
        if (iter.hasNext()) {
          final TimeoutInfo timeout = iter.next();
          Vertx.instance.setTimer(timeout.timeout, new Handler<Long>() {
            public void handle(Long id) {
              response.write(timeout.buff);
              nextTimeout(iter, response);
            }
          });
        } else {
          timeouts.clear();
        }
      }

      public void handle(HttpServerRequest req) {
        req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");
        BaseTransport.setCORS(req.response, "*");
        req.response.setChunked(true);

        Buffer h = Buffer.create(2);
        h.appendString("h\n");

        Buffer hs = Buffer.create(2050);
        for (int i = 0; i < 2048; i++) {
          hs.appendByte((byte)' ');
        }
        hs.appendString("h\n");

        setTimeout(0, h);
        setTimeout(1, hs);
        setTimeout(5, h);
        setTimeout(25, h);
        setTimeout(125, h);
        setTimeout(625, h);
        setTimeout(3125, h);

        runTimeouts(req.response);

      }
    };
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
            sock.close();
          }
        });
        server.installApp("disabled_websocket_echo", false, "/disabled_websocket_echo", new Handler<SockJSSocket>() {
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
    // Catch all - serve a 404
    rm.get(".*", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.statusCode = 404;
        req.response.end();
      }
    });
    server.requestHandler(rm);

    server.websocketHandler(wsMatcher);

    server.listen(8080);
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

