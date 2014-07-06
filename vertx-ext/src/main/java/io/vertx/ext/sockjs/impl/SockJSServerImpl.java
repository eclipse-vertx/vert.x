/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.sockjs.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VoidHandler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.impl.WebSocketMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.ext.routematcher.RouteMatcher;
import io.vertx.ext.routematcher.RouteMatcher;
import io.vertx.ext.sockjs.SockJSServer;

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
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSServerImpl implements SockJSServer, Handler<HttpServerRequest> {

  private static final Logger log = LoggerFactory.getLogger(SockJSServerImpl.class);

  private final Vertx vertx;
  private RouteMatcher rm = RouteMatcher.newRouteMatcher();
  private WebSocketMatcher wsMatcher = new WebSocketMatcher();
  private final Map<String, Session> sessions;
  private EventBusBridgeHook hook;
  private long timerID;

  /*
  Do not call this directly. Use SockJSServer.newSockJSServer(...) instead
   */
  public SockJSServerImpl(Vertx vertx, HttpServer httpServer) {
    this.vertx = vertx;
    this.sessions = vertx.sharedData().getMap("_vertx.sockjssessions");
    // Any previous request and websocket handlers will become default handlers
    // if nothing else matches
    rm.noMatch(httpServer.requestHandler());
    wsMatcher.noMatch(new Handler<WebSocketMatcher.Match>() {
      Handler<ServerWebSocket> wsHandler = httpServer.websocketHandler();
      public void handle(WebSocketMatcher.Match match) {
        if (wsHandler != null) {
          wsHandler.handle(match.ws);
        }
      }
    });
    httpServer.requestHandler(this);
    httpServer.websocketHandler(wsMatcher);
    // Sanity check - a common mistake users make is to set the http request handler AFTER they have created this
    // which overwrites this one.
    timerID = vertx.setPeriodic(5000, new Handler<Long>() {
      @Override
      public void handle(Long timerID) {
        if (httpServer.requestHandler() == null) {
          // Implies server is closed - cancel timer id
          vertx.cancelTimer(timerID);
        } else if (httpServer.requestHandler() != SockJSServerImpl.this) {
          log.warn("You have overwritten the Http server request handler AFTER the SockJSServer has been created " +
                   "which will stop the SockJSServer from functioning. Make sure you set http request handler BEFORE " +
                   "you create the SockJSServer");
        }
      }
    });
  }

  @Override
  public void handle(HttpServerRequest req) {
    if (log.isTraceEnabled()) {
      log.trace("Got request in sockjs server: " + req.uri());
    }
    rm.requestHandler().handle(req);
  }

  public void close() {
    vertx.cancelTimer(timerID);
  }

  private static JsonObject setDefaults(JsonObject config) {
    config = config.copy();
    //Set the defaults
    if (config.getNumber("session_timeout") == null) {
      config.putNumber("session_timeout", 5l * 1000); // 5 seconds default
    }
    if (config.getBoolean("insert_JSESSIONID") == null) {
      config.putBoolean("insert_JSESSIONID", true);
    }
    if (config.getNumber("heartbeat_period") == null) {
      config.putNumber("heartbeat_period", 25l * 1000);
    }
    if (config.getNumber("max_bytes_streaming") == null) {
      config.putNumber("max_bytes_streaming", 128 * 1024);
    }
    if (config.getString("prefix") == null) {
      config.putString("prefix", "/");
    }
    if (config.getString("library_url") == null) {
      config.putString("library_url", "http://cdn.sockjs.org/sockjs-0.3.4.min.js");
    }
    if (config.getArray("disabled_transports") == null) {
      config.putArray("disabled_transports", new JsonArray());
    }
    return config;
  }
  
  public SockJSServerImpl setHook(EventBusBridgeHook hook) {
	  this.hook = hook;
    return this;
  }

  public SockJSServerImpl installApp(JsonObject config,
                                 Handler<SockJSSocket> sockHandler) {

    config = setDefaults(config);

    String prefix = config.getString("prefix");

    if (prefix == null || prefix.equals("") || prefix.endsWith("/")) {
      throw new IllegalArgumentException("Invalid prefix: " + prefix);
    }

    // Base handler for app

    rm.getWithRegEx(prefix + "\\/?", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("Returning welcome response");
        req.response().headers().set("Content-Type", "text/plain; charset=UTF-8");
        req.response().writeStringAndEnd("Welcome to SockJS!\n");
      }
    });

    // Iframe handlers
    String iframeHTML = IFRAME_TEMPLATE.replace("{{ sockjs_url }}", config.getString("library_url"));
    Handler<HttpServerRequest> iframeHandler = createIFrameHandler(iframeHTML);

    // Request exactly for iframe.html
    rm.getWithRegEx(prefix + "\\/iframe\\.html", iframeHandler);

    // Versioned
    rm.getWithRegEx(prefix + "\\/iframe-[^\\/]*\\.html", iframeHandler);

    // Chunking test
    rm.postWithRegEx(prefix + "\\/chunking_test", createChunkingTestHandler());
    rm.optionsWithRegEx(prefix + "\\/chunking_test", BaseTransport.createCORSOptionsHandler(config, "OPTIONS, POST"));

    // Info
    rm.getWithRegEx(prefix + "\\/info", BaseTransport.createInfoHandler(config));
    rm.optionsWithRegEx(prefix + "\\/info", BaseTransport.createCORSOptionsHandler(config, "OPTIONS, GET"));

    // Transports

    Set<String> enabledTransports = new HashSet<>();
    enabledTransports.add(Transport.EVENT_SOURCE.toString());
    enabledTransports.add(Transport.HTML_FILE.toString());
    enabledTransports.add(Transport.JSON_P.toString());
    enabledTransports.add(Transport.WEBSOCKET.toString());
    enabledTransports.add(Transport.XHR.toString());
    for (Object tr : config.getArray("disabled_transports", new JsonArray())) {
      enabledTransports.remove(tr);
    }

    if (enabledTransports.contains(Transport.XHR.toString())) {
      new XhrTransport(vertx, rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.EVENT_SOURCE.toString())) {
      new EventSourceTransport(vertx, rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.HTML_FILE.toString())) {
      new HtmlFileTransport(vertx, rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.JSON_P.toString())) {
      new JsonPTransport(vertx, rm, prefix, sessions, config, sockHandler);
    }
    if (enabledTransports.contains(Transport.WEBSOCKET.toString())) {
      new WebSocketTransport(vertx, wsMatcher, rm, prefix, sessions, config, sockHandler);
      new RawWebSocketTransport(vertx, wsMatcher, rm, prefix, sockHandler);
    }
    // Catch all for any other requests on this app

    rm.getWithRegEx(prefix + "\\/.+", new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (log.isTraceEnabled()) log.trace("Request: " + req.uri() + " does not match, returning 404");
        req.response().setStatusCode(404);
        req.response().end();
      }
    });
    return this;
  }

  public SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted) {
	  EventBusBridge busBridge = new EventBusBridge(vertx, inboundPermitted, outboundPermitted);
    if (hook != null) {
      busBridge.setHook(hook);
    }
    installApp(sjsConfig, busBridge);
    return this;
  }

  public SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
                     long authTimeout) {
	  EventBusBridge busBridge = new EventBusBridge(vertx, inboundPermitted, outboundPermitted, authTimeout);
	  if (hook != null) {
		  busBridge.setHook(hook);
	  }
    installApp(sjsConfig, busBridge);
    return this;
  }

  public SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
                     long authTimeout, String authAddress) {
	  EventBusBridge busBridge = new EventBusBridge(vertx, inboundPermitted, outboundPermitted, authTimeout, authAddress);
	  if (hook != null) {
		  busBridge.setHook(hook);
	  }
    installApp(sjsConfig, busBridge);
    return this;
  }

  public SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
                             JsonObject bridgeConfig) {
    EventBusBridge busBridge = new EventBusBridge(vertx, inboundPermitted, outboundPermitted, bridgeConfig);
    if (hook != null) {
      busBridge.setHook(hook);
    }
    installApp(sjsConfig, busBridge);
    return this;
  }

  private Handler<HttpServerRequest> createChunkingTestHandler() {
    return new Handler<HttpServerRequest>() {

      class TimeoutInfo {
        long timeout;
        Buffer buff;

        TimeoutInfo(long timeout, Buffer buff) {
          this.timeout = timeout;
          this.buff = buff;
        }
      }

      private void setTimeout(List<TimeoutInfo> timeouts, long delay, Buffer buff) {
        timeouts.add(new TimeoutInfo(delay, buff));
      }

      private void runTimeouts(List<TimeoutInfo> timeouts, HttpServerResponse response) {
        Iterator<TimeoutInfo> iter = timeouts.iterator();
        nextTimeout(timeouts, iter, response);
      }

      private void nextTimeout(List<TimeoutInfo> timeouts, Iterator<TimeoutInfo> iter, HttpServerResponse response) {
        if (iter.hasNext()) {
          TimeoutInfo timeout = iter.next();
          vertx.setTimer(timeout.timeout, new Handler<Long>() {
            public void handle(Long id) {
              response.writeBuffer(timeout.buff);
              nextTimeout(timeouts, iter, response);
            }
          });
        } else {
          timeouts.clear();
        }
      }

      public void handle(HttpServerRequest req) {
        req.response().headers().set("Content-Type", "application/javascript; charset=UTF-8");

        BaseTransport.setCORS(req);
        req.response().setChunked(true);

        Buffer h = Buffer.newBuffer(2);
        h.appendString("h\n");

        Buffer hs = Buffer.newBuffer(2050);
        for (int i = 0; i < 2048; i++) {
          hs.appendByte((byte) ' ');
        }
        hs.appendString("h\n");

        List<TimeoutInfo> timeouts = new ArrayList<>();

        setTimeout(timeouts, 0, h);
        setTimeout(timeouts, 1, hs);
        setTimeout(timeouts, 5, h);
        setTimeout(timeouts, 25, h);
        setTimeout(timeouts, 125, h);
        setTimeout(timeouts, 625, h);
        setTimeout(timeouts, 3125, h);

        runTimeouts(timeouts, req.response());

      }
    };
  }

  private Handler<HttpServerRequest> createIFrameHandler(String iframeHTML) {
    String etag = getMD5String(iframeHTML);
    return new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        try {
          if (log.isTraceEnabled()) log.trace("In Iframe handler");
          if (etag != null && etag.equals(req.headers().get("if-none-match"))) {
            req.response().setStatusCode(304);
            req.response().end();
          } else {
            req.response().headers().set("Content-Type", "text/html; charset=UTF-8");
            req.response().headers().set("Cache-Control", "public,max-age=31536000");
            long oneYear = 365 * 24 * 60 * 60 * 1000;
            String expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(System.currentTimeMillis() + oneYear));
            req.response().headers().set("Expires", expires);
            req.response().headers().set("ETag", etag);
            req.response().writeStringAndEnd(iframeHTML);
          }
        } catch (Exception e) {
          log.error("Failed to server iframe", e);
        }
      }
    };
  }

  private static String getMD5String(String str) {
    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(str.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
          sb.append(Integer.toHexString(b + 127));
        }
        return sb.toString();
    }
    catch (Exception e) {
        log.error("Failed to generate MD5 for iframe, If-None-Match headers will be ignored");
        return null;
    }
  }


  private static String IFRAME_TEMPLATE =
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

  // For debug only
//  public static void main(String[] args) throws Exception {
//    Vertx vertx = Vertx.newVertx();
//    HttpServer httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(8081));
//    SockJSServerImpl sjsServer = (SockJSServerImpl)vertx.createSockJSServer(httpServer);
//    sjsServer.installTestApplications();
//    httpServer.listen();
//    Thread.sleep(Long.MAX_VALUE);
//  }

  /*
  These applications are required by the SockJS protocol and QUnit tests
   */
  public void installTestApplications() {
    installApp(new JsonObject().putString("prefix", "/echo")
                               .putNumber("max_bytes_streaming", 4096),
               new Handler<SockJSSocket>() {
      public void handle(SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            sock.writeBuffer(buff);
          }
        });
      }
    });
    installApp(new JsonObject().putString("prefix", "/close")
                               .putNumber("max_bytes_streaming", 4096),
               new Handler<SockJSSocket>() {
      public void handle(SockJSSocket sock) {
        sock.close();
      }
    });
    JsonArray disabled = new JsonArray();
    disabled.add(Transport.WEBSOCKET.toString());
    installApp(new JsonObject().putString("prefix", "/disabled_websocket_echo")
                               .putNumber("max_bytes_streaming", 4096)
                               .putArray("disabled_transports",disabled),
        new Handler<SockJSSocket>() {
          public void handle(SockJSSocket sock) {
            sock.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer buff) {
                sock.writeBuffer(buff);
              }
            });
          }
        });
    installApp(new JsonObject().putString("prefix", "/ticker")
                               .putNumber("max_bytes_streaming", 4096),
               new Handler<SockJSSocket>() {
      public void handle(SockJSSocket sock) {
        long timerID = vertx.setPeriodic(1000, new Handler<Long>() {
          public void handle(Long id) {
            sock.writeBuffer(Buffer.newBuffer("tick!"));
          }
        });
        sock.endHandler(new VoidHandler() {
          public void handle() {
            vertx.cancelTimer(timerID);
          }
        });
      }
    });
    installApp(new JsonObject().putString("prefix", "/amplify")
                               .putNumber("max_bytes_streaming", 4096),
               new Handler<SockJSSocket>() {
      public void handle(SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            String str = data.toString();
            int n = Integer.valueOf(str);
            if (n < 0 || n > 19) {
              n = 1;
            }
            int num = (int)Math.pow(2, n);
            Buffer buff = Buffer.newBuffer(num);
            for (int i = 0; i < num; i++) {
              buff.appendByte((byte)'x');
            }
            sock.writeBuffer(buff);
          }
        });
      }
    });
    installApp(new JsonObject().putString("prefix", "/broadcast")
                               .putNumber("max_bytes_streaming", 4096),
               new Handler<SockJSSocket>() {
      Set<String> connections = vertx.sharedData().getSet("conns");
      public void handle(SockJSSocket sock) {
        connections.add(sock.writeHandlerID());
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            for (String actorID : connections) {
              vertx.eventBus().publish(actorID, buffer);
            }
          }
        });
        sock.endHandler(new VoidHandler() {
          public void handle() {
            connections.remove(sock.writeHandlerID());
          }
        });
      }
    });
    installApp(new JsonObject().putString("prefix", "/cookie_needed_echo")
      .putNumber("max_bytes_streaming", 4096).putBoolean("insert_JSESSIONID", true),
      new Handler<SockJSSocket>() {
        public void handle(SockJSSocket sock) {
          sock.dataHandler(new Handler<Buffer>() {
            public void handle(Buffer buff) {
              sock.writeBuffer(buff);
            }
          });
        }
      });


  }

}

