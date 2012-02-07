package vertx.tests.core.websockets;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InstanceCheckServer implements Verticle {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  private final String id = UUID.randomUUID().toString();

  public void start() {

    server = new HttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {
        tu.checkContext();

        //We add the object id of the server to the set
        SharedData.instance.getSet("instances").add(id);
        SharedData.instance.getSet("connections").add(UUID.randomUUID().toString());

        ws.close();
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
