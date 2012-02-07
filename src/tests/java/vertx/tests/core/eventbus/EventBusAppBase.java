package vertx.tests.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.spi.ClusterManager;
import org.vertx.java.core.eventbus.spi.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.net.ServerID;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.java.tests.core.eventbus.Counter;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class EventBusAppBase extends TestClientBase {

  protected Map<String, Object> data;
  protected EventBus eb;

  class TestEventBus extends EventBus {

    TestEventBus(ServerID serverID, ClusterManager clusterManager) {
      super(serverID, clusterManager);
    }

    public void close(Handler<Void> doneHandler) {
      super.close(doneHandler);
    }
  }

  @Override
  public void start() {
    super.start();

    data = SharedData.instance.getMap("data");

    if (isLocal()) {
      eb = EventBus.instance;
    } else {

      // We force each application to have its own instance of the event bus so we can test them clustering
      // you wouldn't do this in real life (programmatically)

      int port = Counter.portCounter.getAndIncrement();
      ServerID serverID = new ServerID(port, "localhost");
      ClusterManager cm = new HazelcastClusterManager();
      eb = new TestEventBus(serverID, cm);
    }

    tu.appReady();
  }

  @Override
  public void stop() {
    if (!isLocal()) {
      ((TestEventBus)eb).close(new SimpleHandler() {
        public void handle() {
          EventBusAppBase.super.stop();
        }
      });
    } else {
      super.stop();
    }
  }

  protected abstract boolean isLocal();

}
