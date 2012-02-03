package vertx.tests.core.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FanoutServer implements Verticle {

  protected TestUtils tu = new TestUtils();

  private NetServer server;

  public void start() {

    final Set<String> connections = SharedData.instance.getSet("conns");

    server = new NetServer();
    server.connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        tu.checkContext();
        connections.add(socket.writeHandlerID);
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkContext();
            for (String actorID : connections) {
              EventBus.instance.send(actorID, buffer);
            }
          }
        });
        socket.closedHandler(new SimpleHandler() {
          public void handle() {
            tu.checkContext();
            connections.remove(socket.writeHandlerID);
          }
        });
      }
    });
    server.listen(1234);
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
