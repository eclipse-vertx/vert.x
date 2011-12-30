package vertx.tests.java.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private NetServer server;

  protected ContextChecker check;

  public void start() {
    check = new ContextChecker(tu);
    server = new NetServer();
    server.connectHandler(getConnectHandler());
    server.listen(8080);
    tu.appReady();
  }

  public void stop() {
    server.close(new SimpleHandler() {
      public void handle() {
        check.check();
        tu.appStopped();
      }
    });
  }

  protected abstract Handler<NetSocket> getConnectHandler();
}
