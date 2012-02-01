package vertx.tests.core.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private NetServer server;

  private final boolean sendAppReady;

  protected BaseServer(boolean sendAppReady) {
    this.sendAppReady = sendAppReady;
  }

  public void start() {
    server = new NetServer();
    server.connectHandler(getConnectHandler());
    Integer port = SharedData.instance.<String, Integer>getMap("params").get("listenport");
    int p = port == null ? 1234: port;
    server.listen(p);

    if (sendAppReady) {
      tu.appReady();
    }
  }

  public void stop() {
    server.close(new SimpleHandler() {
      public void handle() {
        tu.checkContext();
        tu.appStopped();
      }
    });
  }

  protected abstract Handler<NetSocket> getConnectHandler();
}
