package vertx.tests.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CloseHandlerServer extends BaseServer {

  private static final Logger log = Logger.getLogger(CloseHandlerServer.class);

  protected boolean closeFromServer;

  public CloseHandlerServer() {
    closeFromServer = false;
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      final AtomicInteger counter = new AtomicInteger(0);
      public void handle(final NetSocket sock) {
        check.check();
        sock.endHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            tu.azzert(counter.incrementAndGet() == 1);
          }
        });
        sock.closedHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            tu.azzert(counter.incrementAndGet() == 2);
            tu.testComplete("testServerCloseHandler");
          }
        });
        if (closeFromServer) {
          sock.close();
        }
      }
    };
  }
}
