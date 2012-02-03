package vertx.tests.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClosingServer implements Verticle {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  public void start() {
    server = new HttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkContext();

        req.response.end();

        // close the server

        server.close();
      }
    }).listen(8080);

    tu.appReady();
  }

  public void stop() {
    tu.appStopped();
  }

}
