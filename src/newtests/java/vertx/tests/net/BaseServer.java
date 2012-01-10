package vertx.tests.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private NetServer server;

  protected ContextChecker check;

  private final boolean sendAppReady;

  protected BaseServer(boolean sendAppReady) {
    this.sendAppReady = sendAppReady;
  }

  public void start() {
    check = new ContextChecker(tu);

    server = new NetServer();
    server.connectHandler(getConnectHandler());
    Integer port = SharedData.<String, Integer>getMap("params").get("listenport");
    int p = port == null ? 8080: port;
    server.listen(p);

    if (sendAppReady) {
      tu.appReady();
    }
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
