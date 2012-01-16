package vertx.tests.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DrainingServer extends BaseServer {

  public DrainingServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket sock) {
        tu.checkContext();

        tu.azzert(!sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);

        final Buffer buff = TestUtils.generateRandomBuffer(10000);
        //Send data until the buffer is full
        Vertx.instance.setPeriodic(0, new Handler<Long>() {
          public void handle(Long id) {
            tu.checkContext();
            sock.write(buff);
            if (sock.writeQueueFull()) {
              Vertx.instance.cancelTimer(id);
              sock.drainHandler(new SimpleHandler() {
                public void handle() {
                  tu.checkContext();
                  tu.azzert(!sock.writeQueueFull());
                  // End test after a short delay to give the client some time to read the data
                  Vertx.instance.setTimer(100, new Handler<Long>() {
                    public void handle(Long id) {
                      tu.testComplete();
                    }
                  });
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
