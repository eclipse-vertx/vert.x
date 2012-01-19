package vertx.tests.core.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.net.NetSocket;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CloseHandlerServer extends BaseServer {

  protected boolean closeFromServer;

  public CloseHandlerServer() {
    super(true);
    closeFromServer = false;
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      final AtomicInteger counter = new AtomicInteger(0);
      public void handle(final NetSocket sock) {
        tu.checkContext();
        sock.endHandler(new SimpleHandler() {
          public void handle() {
            tu.checkContext();
            tu.azzert(counter.incrementAndGet() == 1);
          }
        });
        sock.closedHandler(new SimpleHandler() {
          public void handle() {
            tu.checkContext();
            tu.azzert(counter.incrementAndGet() == 2);
            tu.testComplete();
          }
        });
        if (closeFromServer) {
          sock.close();
        }
      }
    };
  }
}
