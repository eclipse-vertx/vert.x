package vertx.tests.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PausingServer extends BaseServer {

  public PausingServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket sock) {
        check.check();
        sock.pause();
        final Handler<Message> resumeHandler = new Handler<Message>() {
          public void handle(Message message) {
            check.check();
            sock.resume();
          }
        };
        EventBus.instance.registerHandler("server_resume", resumeHandler);
        sock.closedHandler(new SimpleHandler() {
          public void handle() {
            check.check();
            EventBus.instance.unregisterHandler("server_resume", resumeHandler);
          }
        });
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            check.check();
            sock.write(buffer);
          }
        });
      }
    };
  }
}
