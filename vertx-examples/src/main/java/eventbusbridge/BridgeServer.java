package eventbusbridge;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.platform.Verticle;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BridgeServer extends Verticle {
  Logger logger;

  public void start() throws Exception {
    logger = container.getLogger();
    
    HttpServer server = vertx.createHttpServer();

    // Also serve the static resources. In real life this would probably be done by a CDN
    server.requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (req.path.equals("/")) req.response.sendFile("eventbusbridge/index.html"); // Serve the index.html
        if (req.path.endsWith("vertxbus.js")) req.response.sendFile("eventbusbridge/vertxbus.js"); // Serve the js
      }
    });

    JsonArray permitted = new JsonArray();
    permitted.add(new JsonObject()); // Let everything through

    ServerHook hook = new ServerHook(logger);

    SockJSServer sockJSServer = vertx.createSockJSServer(server);
    sockJSServer.setupHook(hook);
    sockJSServer.bridge(new JsonObject().putString("prefix", "/eventbus"), permitted, permitted);

    server.listen(8080);
  }
}
