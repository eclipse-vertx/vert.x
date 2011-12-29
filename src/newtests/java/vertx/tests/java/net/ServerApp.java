package vertx.tests.java.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerApp implements VertxApp {

  private TestUtils tu = new TestUtils();

  private NetServer server;

  public void start() {
    server = new NetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            //tu.trace("Server got data: " + buffer.length());
            socket.write(buffer);
          }
        });
      }
    }).listen(8080);
    tu.appReady();
  }

  public void stop() {
    server.close(new SimpleHandler() {
      public void handle() {
        tu.appStopped();
      }
    });
  }
}
