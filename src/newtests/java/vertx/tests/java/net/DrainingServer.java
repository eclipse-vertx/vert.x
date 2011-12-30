package vertx.tests.java.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;
import org.vertx.tests.Utils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DrainingServer extends BaseServer {

  private static final Logger log = Logger.getLogger(DrainingServer.class);

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket sock) {
        check.check();

        // Tell the client to pause
        EventBus.instance.send(new Message("client_pause"));

        tu.azzert(!sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);

        final Buffer buff = Utils.generateRandomBuffer(10000);
        //Send data until the buffer is full
        Vertx.instance.setPeriodic(0, new Handler<Long>() {
          public void handle(Long id) {
            check.check();
            sock.write(buff);
            if (sock.writeQueueFull()) {
              Vertx.instance.cancelTimer(id);
              sock.drainHandler(new SimpleHandler() {
                public void handle() {
                  check.check();
                  tu.azzert(!sock.writeQueueFull());
                  tu.testComplete("testServerDrainHandler");
                }
              });

              // Tell the client to resume
              EventBus.instance.send(new Message("client_resume"));
            }
          }
        });
      }
    };
  }
}
