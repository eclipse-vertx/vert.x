package vertx.tests.websockets;

import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketHandler;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.Set;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InstanceCheckServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  private final String id = UUID.randomUUID().toString();

  public void start() {

    server = new HttpServer().websocketHandler(new WebSocketHandler() {
      public void handle(final WebSocket ws) {
        tu.checkContext();

        //We add the object id of the server to the set
        Set<String> set = SharedData.getSet("instances");
        set.add(id);
        SharedData.getCounter("connections").increment();

        ws.close();
      }

      public boolean accept(String path) {
        return true;
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
