package org.vertx.java.examples.eventbusbridge;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.eventbus.SockJSBridgeHandler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BridgeServer implements Verticle {

  HttpServer server;

  public void start() throws Exception {
    server = new HttpServer();
    SockJSServer sjsServer = new SockJSServer(server);
    sjsServer.installApp(new AppConfig().setPrefix("/eventbus"), new SockJSBridgeHandler());

    // Also serve the static resources. In real life this would probably be done by a CDN
    server.requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (req.path.equals("/")) req.response.sendFile("eventbusbridge/index.html"); // Serve the index.html
        if (req.path.endsWith("vertxbus.js")) req.response.sendFile("eventbusbridge/vertxbus.js"); // Serve the js
      }
    });

    server.listen(1234);
  }

  public void stop() throws Exception {
    server.close();
  }
}
