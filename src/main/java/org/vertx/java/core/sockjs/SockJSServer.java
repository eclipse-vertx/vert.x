package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.WebSocketMatcher;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shareddata.SharedData;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * <p>This is an implementation of the server side part of <a href="https://github.com/sockjs">SockJS</a></p>
 *
 * <p>SockJS enables browsers to communicate with the server using a simple WebSocket-like api for sending
 * and receiving messages. Under the bonnet SockJS chooses to use one of several protocols depending on browser
 * capabilities and what apppears to be working across the network.</p>
 *
 * <p>Available protocols include:</p>
 *
 * <p><ul>
 *   <li>WebSockets</li>
 *   <li>xhr-polling</li>
 *   <li>xhr-streaming</li>
 *   <li>json-polling</li>
 *   <li>event-source</li>
 *   <li>html-file</li>
 * </ul></p>
 *
 * <p>This means, it should <i>just work</i> irrespective of what browser is being used, and whether there are nasty
 * things like proxies and load balancers between the client and the server.</p>
 *
 * <p>For more detailed information on SockJS, see their website.</p>
 *
 * <p>On the server side, you interact using instances of {@link SockJSSocket} - this allows you to send data to the
 * client or receive data via the {@link SockJSSocket#dataHandler}.</p>
 *
 * <p>You can register multiple applications with the same SockJSServer, each using different path prefixes, each
 * application will have its own handler, and configuration as described by {@link AppConfig}</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSServer {

  private static final Logger log = Logger.getLogger(SockJSServer.class);

  private RouteMatcher rm = new RouteMatcher();
  private WebSocketMatcher wsMatcher = new WebSocketMatcher();
  private final Map<String, Session> sessions = new HashMap<>();

  /**
   * Create a new SockJSServer.
   * @param httpServer - you must pass in an HttpServer instance
   */
  public SockJSServer(HttpServer httpServer) {
    Handler<HttpServerRequest> prevHandler = httpServer.requestHandler();
    final Handler<ServerWebSocket> wsHandler = httpServer.websocketHandler();

    httpServer.requestHandler(rm);
    httpServer.websocketHandler(wsMatcher);

    // Preserve any previous handlers as the default handlers, if the requests don't match any app URLs they
    // will be called

    if (prevHandler != null) {
      rm.all(".*", prevHandler);
    }

    if (wsHandler != null) {
      wsMatcher.addRegEx(".*", new Handler<WebSocketMatcher.Match>() {
        public void handle(WebSocketMatcher.Match match) {
          wsHandler.handle(match.ws);
        }
      });
    }
  }

  /**
   * Install an application
   * @param config The application configuration
   * @param sockHandler A handler that will be called when new SockJS sessions are created
   */
  public void installApp(AppConfig config,
                         final Handler<SockJSSocket> sockHandler) {
    String prefix = config.getPrefix();

    if (prefix == null || prefix.equals("") || prefix.endsWith("/")) {
      throw new IllegalArgumentException("Invalid prefix: " + prefix);
    }

    // Base handler for app

    rm.getWithRegEx(prefix + "\\/?", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.putHeader("Content-Type", "text/plain; charset=UTF-8");
        req.response.end("Welcome to SockJS!\n");
      }
    });

    // Iframe handlers
    String iframeHTML = IFRAME_TEMPLATE.replace("{{ sockjs_url }}", config.getLibraryURL());
    Handler<HttpServerRequest> iframeHandler = createIFrameHandler(iframeHTML);

    // Request exactly for iframe.html
    rm.getWithRegEx(prefix + "\\/iframe\\.html", iframeHandler);

    // Versioned
    rm.getWithRegEx(prefix + "\\/iframe-[^\\/]*\\.html", iframeHandler);

    // Chunking test
    rm.postWithRegEx(prefix + "\\/chunking_test", createChunkingTestHandler());
    rm.optionsWithRegEx(prefix + "\\/chunking_test", BaseTransport.createCORSOptionsHandler(config, "OPTIONS, POST"));

    // Transports

    Set<Transport> enabledTransports = new HashSet<>();
    enabledTransports.add(Transport.EVENT_SOURCE);
    enabledTransports.add(Transport.HTML_FILE);
    enabledTransports.add(Transport.JSON_P);
    enabledTransports.add(Transport.WEBSOCKETS);
    enabledTransports.add(Transport.XHR);
    for (Transport tr : config.getDisabledTransports()) {
      enabledTransports.remove(tr);
    }

    if (enabledTransports.contains(Transport.XHR)) {
      new XhrTransport(rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.EVENT_SOURCE)) {
      new EventSourceTransport(rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.HTML_FILE)) {
      new HtmlFileTransport(rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.JSON_P)) {
      new JsonPTransport(rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.WEBSOCKETS)) {
      new WebSocketTransport(wsMatcher, rm, prefix, sessions, config, sockHandler);
    }
    // Catch all for any other requests on this app

    rm.getWithRegEx(prefix + "\\/.+", new Handler<HttpServerRequest>() {
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

      private void setTimeout(List<TimeoutInfo> timeouts, long delay, final Buffer buff) {
        timeouts.add(new TimeoutInfo(delay, buff));
      }

      private void runTimeouts(List<TimeoutInfo> timeouts, HttpServerResponse response) {
        final Iterator<TimeoutInfo> iter = timeouts.iterator();
        nextTimeout(timeouts, iter, response);
      }

      private void nextTimeout(final List<TimeoutInfo> timeouts, final Iterator<TimeoutInfo> iter, final HttpServerResponse response) {
        if (iter.hasNext()) {
          final TimeoutInfo timeout = iter.next();
          Vertx.instance.setTimer(timeout.timeout, new Handler<Long>() {
            public void handle(Long id) {
              response.write(timeout.buff);
              nextTimeout(timeouts, iter, response);
            }
          });
        } else {
          timeouts.clear();
        }
      }

      public void handle(HttpServerRequest req) {
        req.response.putHeader("Content-Type", "application/javascript; charset=UTF-8");

        BaseTransport.setCORS(req);
        req.response.setChunked(true);

        Buffer h = Buffer.create(2);
        h.appendString("h\n");

        Buffer hs = Buffer.create(2050);
        for (int i = 0; i < 2048; i++) {
          hs.appendByte((byte) ' ');
        }
        hs.appendString("h\n");

        List<TimeoutInfo> timeouts = new ArrayList<>();

        setTimeout(timeouts, 0, h);
        setTimeout(timeouts,1, hs);
        setTimeout(timeouts,5, h);
        setTimeout(timeouts,25, h);
        setTimeout(timeouts,125, h);
        setTimeout(timeouts,625, h);
        setTimeout(timeouts,3125, h);

        runTimeouts(timeouts, req.response);

      }
    };
  }

  private Handler<HttpServerRequest> createIFrameHandler(final String iframeHTML) {
    return new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {

        try {
          String etag = getMD5String(iframeHTML);
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

  private String getMD5String(final String str) throws Exception {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] bytes = md.digest(str.getBytes("UTF-8"));
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(Integer.toHexString(b + 127));
    }
    return sb.toString();
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
          "<payload>\n" +
          "  <h2>Don't panic!</h2>\n" +
          "  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>\n" +
          "</payload>\n" +
          "</html>";

  // For debug only
  public static void main(String[] args) throws Exception {
    VertxInternal.instance.startOnEventLoop(new Runnable() {
      public void run() {
        HttpServer httpServer = new HttpServer();
        SockJSServer sjsServer = new SockJSServer(httpServer);
        sjsServer.installTestApplications();
        httpServer.listen(8080);
      }
    });

    Thread.sleep(Long.MAX_VALUE);
  }

  /*
  These applications are required by the SockJS protocol and QUnit tests
   */
  public void installTestApplications() {
    installApp(new AppConfig().setPrefix("/echo"), new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            sock.writeBuffer(buff);
          }
        });
      }
    });
    installApp(new AppConfig().setPrefix("/close"), new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        sock.close();
      }
    });
    Set<Transport> disabled = new HashSet<>();
    disabled.add(Transport.WEBSOCKETS);
    installApp(new AppConfig().setPrefix("/disabled_websocket_echo").setDisabledTransports(disabled),
        new Handler<SockJSSocket>() {
          public void handle(final SockJSSocket sock) {
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer buff) {
                sock.writeBuffer(buff);
              }
            });
          }
        });
    installApp(new AppConfig().setPrefix("/ticker"), new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        final long timerID = Vertx.instance.setPeriodic(1000, new Handler<Long>() {
          public void handle(Long id) {
            sock.writeBuffer(Buffer.create("tick!"));
          }
        });
        sock.endHandler(new SimpleHandler() {
          public void handle() {
            Vertx.instance.cancelTimer(timerID);
          }
        });
      }
    });
    installApp(new AppConfig().setPrefix("/amplify"), new Handler<SockJSSocket>() {
      long timerID;
      public void handle(final SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            String str = data.toString();
            int n = Integer.valueOf(str);
            if (n < 0 || n > 19) {
              n = 1;
            }
            int num = (int)Math.pow(2, n);
            Buffer buff = Buffer.create(num);
            for (int i = 0; i < num; i++) {
              buff.appendByte((byte)'x');
            }
            sock.writeBuffer(buff);
          }
        });
      }
    });
    installApp(new AppConfig().setPrefix("/broadcast"), new Handler<SockJSSocket>() {
      final Set<String> connections = SharedData.instance.getSet("conns");
      public void handle(final SockJSSocket sock) {
        connections.add(sock.writeHandlerID);
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            for (String actorID : connections) {
              EventBus.instance.send(actorID, buffer);
            }
          }
        });
        sock.endHandler(new SimpleHandler() {
          public void handle() {
            connections.remove(sock.writeHandlerID);
          }
        });
      }
    });

  }

}

