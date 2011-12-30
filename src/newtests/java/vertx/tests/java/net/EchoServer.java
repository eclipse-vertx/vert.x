package vertx.tests.java.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EchoServer extends BaseServer {

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        check.check();
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            check.check();
            socket.write(buffer);
          }
        });
      }
    };
  }
}
