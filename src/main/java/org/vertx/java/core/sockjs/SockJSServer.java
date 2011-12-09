package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.WebSocketMatcher;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shared.SharedData;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSServer {

  private static final Logger log = Logger.getLogger(SockJSServer.class);

  private static final String DEFAULT_LIBRARY_URL = "http://cdn.sockjs.org/sockjs-0.1.min.js";

  //TODO heartbeat

  private final String iframeHTML;
  private RouteMatcher rm = new RouteMatcher();
  private WebSocketMatcher wsMatcher = new WebSocketMatcher();
  private final Map<String, Session> sessions = SharedData.getMap("sockjs_sessions");
  private String libraryURL = DEFAULT_LIBRARY_URL;

  public static void main(String[] args) throws Exception {
    VertxInternal.instance.go(new Runnable() {
      public void run() {
        HttpServer httpServer = new HttpServer();
        new SockJSServer(httpServer, null);
        httpServer.listen(8080);
      }
    });

    Thread.sleep(1000000);
  }

  //TODO all these params

  public SockJSServer(HttpServer httpServer, String libraryURL,
                      int responseLimit,
                      boolean insertJSESSSIONID,
                      long heartbeatPeriod,
                      long disconnectDelay) {
    if (libraryURL != null) {
      this.libraryURL = libraryURL;
    }
    iframeHTML = IFRAME_TEMPLATE.replace("{{ sockjs_url }}", this.libraryURL);

    httpServer.requestHandler(rm);
    httpServer.websocketHandler(wsMatcher);
    installDefaultApps();
    // Catch all - serve a 404
    rm.get(".*", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.statusCode = 404;
        req.response.end();
      }
    });
  }

  public void installApp(Set<Transport> disabledTransports, String basePath,
                         final Handler<SockJSSocket> sockHandler) {

    if (basePath == null || basePath.equals("") || basePath.endsWith("/")) {
      throw new IllegalArgumentException("Invalid base path: " + basePath);
    }

    log.info("Installing app: " + basePath);

    Set<Transport> enabledTransports = new HashSet<>();
    enabledTransports.add(Transport.EVENT_SOURCE);
    enabledTransports.add(Transport.HTML_FILE);
    enabledTransports.add(Transport.JSON_P);
    enabledTransports.add(Transport.WEBSOCKETS);
    enabledTransports.add(Transport.XHR);

    if (disabledTransports != null) {
      for (Transport tr: disabledTransports) {
        enabledTransports.remove(tr);
      }
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

    if (enabledTransports.contains(Transport.XHR)) {
      new XHRTransport(sessions).init(rm, basePath, sockHandler);
    }
    if (enabledTransports.contains(Transport.EVENT_SOURCE)) {
      new EventSourceTransport(sessions).init(rm, basePath, sockHandler);
    }
    if (enabledTransports.contains(Transport.HTML_FILE)) {
      new HtmlFileTransport(sessions).init(rm, basePath, sockHandler);
    }
    if (enabledTransports.contains(Transport.JSON_P)) {
      new JSONPTransport(sessions).init(rm, basePath, sockHandler);
    }
    if (enabledTransports.contains(Transport.WEBSOCKETS)) {
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

  private void installDefaultApps() {
    // Install the default apps
    installApp(null, "/echo", new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            sock.write(buff);
          }
        });
      }
    });
    installApp(null, "/close", new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        sock.close();
      }
    });
    Set<Transport> disabled = new HashSet<>();
    disabled.add(Transport.WEBSOCKETS);
    installApp(disabled, "/disabled_websocket_echo", new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            sock.write(buff);
          }
        });
      }
    });
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

