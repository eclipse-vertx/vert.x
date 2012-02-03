package vertx.tests.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PausingServer implements Verticle {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  public void start() {
    server = new HttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkContext();
        req.response.setChunked(true);
        req.pause();
        final Handler<Message<Buffer>> resumeHandler = new Handler<Message<Buffer>>() {
          public void handle(Message message) {
            tu.checkContext();
            req.resume();
          }
        };
        EventBus.instance.registerHandler("server_resume", resumeHandler);
        req.endHandler(new SimpleHandler() {
          public void handle() {
            tu.checkContext();
            EventBus.instance.unregisterHandler("server_resume", resumeHandler);
          }
        });
        req.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkContext();
            req.response.write(buffer);
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
