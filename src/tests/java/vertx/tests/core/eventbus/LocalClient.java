package vertx.tests.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
    eb.send(new Message("some-address", buff));
  }

  public void testPubSubMultipleHandlers() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    eb.send(new Message("some-address", buff));
  }

  public void testNoBuffer() {
    eb.send(new Message("some-address"));
  }

  public void testNullBuffer() {
    eb.send(new Message("some-address", null));
  }

  public void testPointToPoint() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    Set<String> addresses = SharedData.getSet("addresses");
    for (String address: addresses) {
      eb.send(new Message(address, buff));
    }
  }

  public void testReply() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    Set<String> addresses = SharedData.getSet("addresses");
    for (final String address: addresses) {
      eb.send(new Message(address, buff), new Handler<Message>() {
        public void handle(Message reply) {
          tu.azzert(reply.address != null);
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
      eb.registerHandler(address, new Handler<Message>() {
        boolean handled;
        public void handle(Message msg) {
          tu.checkContext();
          tu.azzert(!handled);
          tu.azzert(TestUtils.buffersEqual(buff, msg.body));
          tu.azzert(address.equals(msg.address));
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

    eb.send(new Message(address, buff));
  }


}
