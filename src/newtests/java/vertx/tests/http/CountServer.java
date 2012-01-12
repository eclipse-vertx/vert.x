package vertx.tests.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CountServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  protected ContextChecker check;

  public void start() {
    check = new ContextChecker(tu);

    server = new HttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        check.check();
        req.response.putHeader("count", req.getHeader("count"));
        req.response.end();
      }
    }).listen(8080);

    tu.appReady();
  }

  public void stop() {
    server.close(new SimpleHandler() {
      public void handle() {
        check.check();
        tu.appStopped();
      }
    });
  }

}
