package vertx.tests.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.ContextChecker;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InstanceCheckServer extends BaseServer {

  private static final Logger log = Logger.getLogger(InstanceCheckServer.class);

  public InstanceCheckServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {

      public void handle(final NetSocket socket) {

        check.check();
        //We add the object id of the server to the set
        SharedData.getSet("instances").add(System.identityHashCode(InstanceCheckServer.this));
        SharedData.getCounter("connections").increment();

        socket.close();
      }
    };
  }
}
