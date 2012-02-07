package vertx.tests.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalClient extends EventBusAppBase {

  @Override
  public void start() {
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
  }

  protected boolean isLocal() {
    return true;
  }

  public void testPubSub() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    eb.send("some-address", buff);
  }

  public void testPubSubMultipleHandlers() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    eb.send("some-address", buff);
  }

  public void testPointToPoint() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    Set<String> addresses = SharedData.instance.getSet("addresses");
    for (String address: addresses) {
      eb.send(address, buff);
    }

  }

  public void testReply() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    Set<String> addresses = SharedData.instance.getSet("addresses");
    for (final String address: addresses) {
      eb.send(address, buff, new Handler<Message<Buffer>>() {
        public void handle(Message<Buffer> reply) {
          tu.azzert(("reply" + address).equals(reply.body.toString()));
          tu.testComplete();
        }
      });
    }
  }

  public void testLocal() {
    final int numHandlers = 10;
    final String address = UUID.randomUUID().toString();
    final AtomicInteger count = new AtomicInteger(0);
    final Buffer buff = TestUtils.generateRandomBuffer(1000);
    for (int i = 0; i < numHandlers; i++) {
      eb.registerHandler(address, new Handler<Message<Buffer>>() {
        boolean handled;

        public void handle(Message<Buffer> msg) {
          tu.checkContext();
          tu.azzert(!handled);
          tu.azzert(TestUtils.buffersEqual(buff, msg.body));
          int c = count.incrementAndGet();
          tu.azzert(c <= numHandlers);
          eb.unregisterHandler(address, this);
          if (c == numHandlers) {
            tu.testComplete();
          }
          handled = true;
        }
      });
    }

    eb.send(address, buff);
  }

  public void testRegisterNoAddress() {
    final String msg = "foo";
    final AtomicReference<String> idRef = new AtomicReference<>();
    String id = eb.registerHandler(new Handler<Message<String>>() {
      boolean handled = false;
      public void handle(Message<String> received) {
        tu.azzert(!handled);
        tu.azzert(msg.equals(received.body));
        handled = true;
        eb.unregisterHandler(idRef.get());
        Vertx.instance.setTimer(100, new Handler<Long>() {
          public void handle(Long timerID) {
            tu.testComplete();
          }
        });
      }
    });
    idRef.set(id);
    for (int i = 0; i < 10; i++) {
      eb.send(id, "foo");
    }
  }

}
