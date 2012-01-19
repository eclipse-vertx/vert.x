package vertx.tests.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DrainingServer implements VertxApp {

  protected TestUtils tu = new TestUtils();
  private HttpServer server;

  public void start() {
    server = new HttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkContext();

        req.response.setChunked(true);

        tu.azzert(!req.response.writeQueueFull());
        req.response.setWriteQueueMaxSize(1000);

        final Buffer buff = TestUtils.generateRandomBuffer(10000);
        //Send data until the buffer is full
        Vertx.instance.setPeriodic(0, new Handler<Long>() {
          public void handle(Long id) {
            tu.checkContext();
            req.response.write(buff);
            if (req.response.writeQueueFull()) {
              Vertx.instance.cancelTimer(id);
              req.response.drainHandler(new SimpleHandler() {
                public void handle() {
                  tu.checkContext();
                  tu.azzert(!req.response.writeQueueFull());
                  tu.testComplete();
                }
              });

              // Tell the client to resume
              EventBus.instance.send(new Message("client_resume"));
            }
          }
        });
      }
    }).listen(8080);

    tu.appReady();
  }

  public void stop() {
    server.close(new SimpleHandler() {
      public void handle() {
        tu.checkContext();
        tu.appStopped();
      }
    });
  }

}
