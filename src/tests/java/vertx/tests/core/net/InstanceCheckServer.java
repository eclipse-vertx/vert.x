package vertx.tests.core.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InstanceCheckServer extends BaseServer {

  private final String id = UUID.randomUUID().toString();

  public InstanceCheckServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {

      public void handle(final NetSocket socket) {

        tu.checkContext();
        //We add the object id of the server to the set
        SharedData.getSet("instances").add(id);
        SharedData.getSet("connections").add(UUID.randomUUID().toString());

        socket.close();
      }
    };
  }
}
