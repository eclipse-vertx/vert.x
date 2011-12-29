package vertx.tests.java.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.newtests.VertxTestApp;
import org.vertx.tests.Utils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClientApp extends VertxTestApp {

  private static final Logger log = Logger.getLogger(ClientApp.class);

  private NetClient client;

  public void start() {
    client = new NetClient();

    tu.register("test1", new SimpleHandler() {
      public void handle() {
        test1();
      }
    });

    tu.appReady();
  }

  public void stop() {
    client.close();
    tu.appStopped();
  }

  // The tests

  void test1() {
    client.connect(8080, "localhost", new Handler<NetSocket>() {
      public void handle(NetSocket socket) {

        final int numChunks = 100;
        final int chunkSize = 100;

        final Buffer received = Buffer.create(0);
        final Buffer sent = Buffer.create(0);

        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.trace("Client received data: " + buffer.length());
            received.appendBuffer(buffer);
            if (received.length() == numChunks * chunkSize) {
              tu.azzert(Utils.buffersEqual(sent, received));
              tu.testComplete("test1");
            }
          }
        });

        //Now send some data
        for (int i = 0; i < numChunks; i++) {
          Buffer buff = Utils.generateRandomBuffer(chunkSize);
          sent.appendBuffer(buff);
          socket.write(buff);
        }
      }
    });
  }
}
