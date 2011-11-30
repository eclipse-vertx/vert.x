package org.vertx.tests.core.eventbus;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.cluster.EventBus;
import org.vertx.java.core.cluster.Message;
import org.vertx.java.core.cluster.spi.ClusterManager;
import org.vertx.java.core.cluster.spi.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.ServerID;
import org.vertx.tests.core.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Set up several handlers for same name
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusTest extends TestBase {

  private static final Logger log = Logger.getLogger(EventBusTest.class);

  class TestEventBus extends EventBus {

    TestEventBus(ServerID serverID, ClusterManager clusterManager) {
      super(serverID, clusterManager);
    }
  }

  @Test
  public void test1() throws Exception {

    final int numMessages = 100;
    final int numHandlers = 3;
    final String subName = "sub";
    final CountDownLatch latch = new CountDownLatch(numHandlers * numMessages);

    VertxInternal.instance.go(new Runnable() {
      public void run() {
        int basePort = 25500;

        EventBus eb = null;
        for (int i = 0; i < numHandlers; i++) {
          ServerID id = new ServerID(basePort++, "localhost");
          ClusterManager cm = createClusterManager();
          eb = new TestEventBus(id, cm);
          eb.registerHandler(subName, new Handler<Message>() {
            public void handle(Message msg) {
              //log.info("handler1 got msg");
              latch.countDown();
            }
          });
        }

        for (int i = 0; i < numMessages; i++) {
          eb.send(new Message(subName, Buffer.create("foo")));
          log.info("Sent message");
        }
      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();

  }

  protected ClusterManager createClusterManager() {
    return new HazelcastClusterManager();
  }
}
