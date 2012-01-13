package vertx.tests.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CloseSocketServer extends BaseServer {

  public CloseSocketServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        check.check();
        socket.close();
      }
    };
  }
}
