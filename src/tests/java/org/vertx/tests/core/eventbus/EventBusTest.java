package org.vertx.tests.core.eventbus;

import com.hazelcast.core.Hazelcast;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.cluster.EventBus;
import org.vertx.java.core.cluster.Message;
import org.vertx.java.core.cluster.spi.ClusterManager;
import org.vertx.java.core.cluster.spi.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.ServerID;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1) Handlers with same name on different nodes, send from all to that sub, make sure it arrives
 * 2) Handlers with same name on different nodes, send from all to different sub, make sure it doesn't arrive
 * 3) Handlers with same name on same node, send to that sub from all , assert it arrives
 * 4) Handlers with same name on same node, send to different sub from all, assert it doesn't arrives
 * 5) Handlers with same name on same and different node, send to same sub all, assert arrives
 * 6) Handlers with same name on same and different nodes, sent to different sub, assert doesn't arrive
 * 7) Handlers with different names on different nodes, sent to one from all, make sure arrives at only matching ones.
 * 8) Send with acknowledgement, make sure get ack back
 * 9) Check messageID, sender, payload, make sure payload is copied (especially for local routings)
 *
 * Summary:
 *
 * setNumServers()
 *
 * addHandler(serverNum, address)
 * addSender(serverNum, numMessages, address)
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

    public void close(Handler<Void> done) {
      super.close(done);
    }
  }

  private int numBuses = 3;
  private List<TestEventBus> buses = new ArrayList<>();
  private List<TestHandler> handlers = new ArrayList<>();
  private MessageCounter messageCounter;
  private List<Method> methodsList = new ArrayList<>();
  private Iterator<Method> iter;
  private ContextChecker checker;
  private List<TestHandler> handlersToAdd = new ArrayList<>();

  @AfterClass
  public void tearDown() {
    super.tearDown();
    Hazelcast.shutdownAll();
  }

  @Test
  public void test() throws Exception {
    Method[] methods = EventBusTest.class.getMethods();
    for (Method meth: methods) {
      if (meth.getName().startsWith("doTest")) {
        methodsList.add(meth);
      }
    }
    iter = methodsList.iterator();
    final CountDownLatch latch = new CountDownLatch(1);
    final Handler<Void> doneHandler = new SimpleHandler() {
      public void handle() {
        runNextMethod(this, latch);
      }
    };
    VertxInternal.instance.go(new Runnable() {
      public void run() {
        try {
          checker = new ContextChecker();
          setupBuses();
          runNextMethod(doneHandler, latch);
        } catch (Exception e) {
          azzert(false, "Caught exception " + e.getMessage());
        }

      }
    });
    azzert(latch.await(10, TimeUnit.SECONDS));
    Thread.sleep(250); // If too many msgs sent, allows them to be registered
    throwAssertions();
  }

  private void runNextMethod(Handler<Void> doneHandler, final CountDownLatch latch) {
    if (iter.hasNext()) {
      Method meth = iter.next();
      try {
        init();
        log.info("********** RUNNING TEST: " + meth.getName());
        meth.invoke(this, doneHandler);
      } catch (Exception e) {
        log.error("Exception in invocation", e);
        azzert(false, "Exception thrown: " + e.getMessage());
      }
    } else {
      //Close the buses and finish
      Handler<Void> cumulativeHandler = new SimpleHandler() {
        int count;
        public void handle() {
          if (++count == numBuses) {
            latch.countDown();
          }
        }
      };
      for (TestEventBus bus: buses) {
        bus.close(cumulativeHandler);
      }
    }
  }

  public void doTestLocalHandler(Handler<Void> done) throws Exception {
    addHandler(0, "sub1");
    expectedMessages(1);
    sendMessage(done, 0, "sub1");
  }

  public void doTestNonLocalHandler(Handler<Void> done) throws Exception {
    addHandler(1, "sub1");
    expectedMessages(1);
    sendMessage(done, 0, "sub1");
  }

  public void doTestHandlersAll(Handler<Void> done) throws Exception {
    addHandler(0, "sub1");
    addHandler(1, "sub1");
    addHandler(2, "sub1");
    expectedMessages(3);
    sendMessage(done, 0, "sub1");
  }

  public void doTestHandlersAllMultiple(Handler<Void> done) throws Exception {
    addHandler(0, "sub1");
    addHandler(0, "sub1");
    addHandler(1, "sub1");
    addHandler(1, "sub1");
    addHandler(2, "sub1");
    addHandler(2, "sub1");
    expectedMessages(6);
    sendMessage(done, 0, "sub1");
  }

  public void doTestNonMatchingHandlers(Handler<Void> done) throws Exception {
    addHandler(0, "sub2");
    addHandler(1, "sub2");
    addHandler(2, "sub2");
    addHandler(1, "sub1");
    expectedMessages(1);
    sendMessage(done, 0, "sub1");
  }

  public void doTestLocalHandlerAck(Handler<Void> done) throws Exception {
    addHandler(0, "sub1");
    expectedMessages(1);
    sendMessage(done, 0, "sub1", true);
  }

  public void doTestNonLocalHandlerAck(Handler<Void> done) throws Exception {
    addHandler(1, "sub1");
    expectedMessages(1);
    sendMessage(done, 0, "sub1", true);
  }

  public void doTestMultipleHandlerAcks(Handler<Void> done) throws Exception {
    addHandler(0, "sub1");
    addHandler(1, "sub1");
    expectedMessages(2);
    sendMessage(done, 0, "sub1", true);
  }

  private void init() {
    messageCounter = new MessageCounter();
  }

  private void reset() {
    for (TestHandler handler: handlers) {
      buses.get(handler.pos).unregisterHandler(handler.subName, handler);
    }
    handlers.clear();
    handlersToAdd.clear();
  }

  private void expectedMessages(int messages) {
    messageCounter.count = messages;
  }

  private void setupBuses() throws Exception {
    int basePort = 25500;
    for (int i = 0; i < numBuses; i++) {
      ServerID id = new ServerID(basePort++, "localhost");
      ClusterManager cm = createClusterManager();
      buses.add(new TestEventBus(id, cm));
    }
  }

  private void addHandler(int pos, String subName) {
    TestHandler handler = new TestHandler(pos, subName);
    handlersToAdd.add(handler);
  }

  private void sendMessage(final Handler<Void> done, int pos, String address) {
    sendMessage(done, pos, address, false);
  }

  private void sendMessage(final Handler<Void> done, final int pos, final String address, final boolean ack) {
    CompletionHandler<Void> handler = new CompletionHandler<Void>() {
      AtomicInteger count = new AtomicInteger(handlersToAdd.size());
      public void handle(Future<Void> event) {
        if (event.succeeded()) {
          if (count.decrementAndGet() == 0) {
            // Actually send the message

            Buffer buff = Utils.generateRandomBuffer(1000);
            Message msg = new Message(address, buff);
            EventBus bus = buses.get(pos);
            if (ack) {
              Handler<Void> dHandler = new SimpleHandler() {
                int count;
                public void handle() {
                  if (++count == 2) {
                    // Must wait for for it to be called twice
                    done.handle(null);
                  }
                }
              };
              bus.send(msg, dHandler);
              assertReceived(dHandler, msg);
            } else {
              bus.send(msg);
              assertReceived(done, msg);
            }
          }
        } else {
          log.error("Failed to propagate", event.exception());
          azzert(false);
        }
      }
    };

    // We need to wait for the handlers to be added and propagated across the cluster before sending, otherwise
    // race conditions may ensue
    for (TestHandler msgHandler: handlersToAdd) {
      buses.get(msgHandler.pos).registerHandler(msgHandler.subName, msgHandler, handler);
      handlers.add(msgHandler);
    }
  }

  private void assertReceived(final Handler<Void> done, final Message msg) {
    messageCounter.doneHandler(new SimpleHandler() {
      public void handle() {
        for (TestHandler handler: handlers) {
          if (handler.subName.equals(msg.address)) {
            azzert(handler.message != null);
            azzert(handler.message.messageID.equals(msg.messageID), "Expected " + msg.messageID + " Actual: " + handler.message.messageID);
            azzert(handler.message.address.equals(msg.address));
            azzert(Utils.buffersEqual(msg.body, handler.message.body));
            azzert(msg.body != handler.message.body); // Should be copied
            azzert(msg != handler.message);
          }
        }
        reset();
        done.handle(null);
      }
    });
  }

  protected ClusterManager createClusterManager() {
    return new HazelcastClusterManager();
  }

  class MessageCounter {
    int count;
    Handler<Void> doneHandler;

    void onReceived() {
      if (--count == 0) {
        doneHandler.handle(null);
      }
      if (count < 0) {
        azzert(false, "Too many messages received");
      }
    }

    void doneHandler(Handler<Void> doneHandler) {
      this.doneHandler = doneHandler;
      if (count == 0) {
        doneHandler.handle(null);
      }
    }
  }


  class TestHandler implements Handler<Message> {
    final int pos;
    final String subName;
    Message message;

    TestHandler(int pos, String subName) {
      this.pos = pos;
      this.subName = subName;
    }

    public void handle(Message msg) {
      checker.check();
      this.message = msg;
      messageCounter.onReceived();
      msg.acknowledge();
    }
  }
}
