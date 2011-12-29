package vertx.tests.java.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.VertxTestApp;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClientApp extends VertxTestApp {

  private static final Logger log = Logger.getLogger(ClientApp.class);

  private NetClient client;

  public void start() {
    client = new NetClient();

    tu.register("testEcho", new SimpleHandler() {
      public void handle() {
        testEcho();
      }
    });

    tu.register("testConnect", new SimpleHandler() {
      public void handle() {
        testConnect();
      }
    });

    tu.register("testWriteWithCompletion", new SimpleHandler() {
      public void handle() {
        log.info("**** calling testWriteWithCompletion");
        testWriteWithCompletion();
      }
    });

    tu.appReady();
  }

  public void stop() {
    client.close();

    tu.unregister("testEcho");
    tu.unregister("testConnect");
    tu.unregister("testWriteWithCompletion");

    tu.appStopped();
  }

  // The tests

  void testEcho() {
    client.connect(8080, "localhost", new Handler<NetSocket>() {
      public void handle(NetSocket socket) {

        final int numChunks = 100;
        final int chunkSize = 100;

        final Buffer received = Buffer.create(0);
        final Buffer sent = Buffer.create(0);

        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            //tu.trace("Client received data: " + buffer.length());
            received.appendBuffer(buffer);
            if (received.length() == numChunks * chunkSize) {
             // tu.azzert(Utils.buffersEqual(sent, received));
              tu.testComplete("testEcho");
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

  void testConnect() {
    final int numConnections = 1000;
    final AtomicInteger connCount = new AtomicInteger(0);
    for (int i = 0; i < numConnections; i++) {
      client.connect(8080, new Handler<NetSocket>() {
        public void handle(NetSocket sock) {
          sock.close();
          if (connCount.incrementAndGet() == numConnections) {
            tu.testComplete("testConnect");
          }
        }
      });
    }
  }

  void testWriteWithCompletion() {

    final int numSends = 10;
    final int sendSize = 100;
    final Buffer sentBuff = Buffer.create(0);

    client.connect(8080, new Handler<NetSocket>() {
      public void handle(NetSocket sock) {
        final ContextChecker checker = new ContextChecker(tu);

        log.info("**** calling doWrite");

        doWrite(sentBuff, sock, numSends, sendSize, checker);
      }
    });

  }

  //Recursive - we don't write the next packet until we get the completion back from the previous write
  private void doWrite(final Buffer sentBuff, final NetSocket sock, int count, final int sendSize,
                       final ContextChecker checker) {
    Buffer b = Utils.generateRandomBuffer(sendSize);
    sentBuff.appendBuffer(b);
    count--;
    final int c = count;
    if (count == 0) {
      sock.write(b, new SimpleHandler() {
        public void handle() {
          //checker.check();
          tu.testComplete("testWriteWithCompletion");
        }
      });
    } else {
      sock.write(b, new SimpleHandler() {
        public void handle() {
         // checker.check();
          doWrite(sentBuff, sock, c, sendSize, checker);
        }
      });
    }
  }
}
