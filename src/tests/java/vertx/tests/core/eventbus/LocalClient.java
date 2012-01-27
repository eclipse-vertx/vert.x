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
    eb.sendBinary("some-address", buff);
  }

  public void testPubSubMultipleHandlers() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    eb.sendBinary("some-address", buff);
  }

  public void testPointToPoint() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    Set<String> addresses = SharedData.getSet("addresses");
    for (String address: addresses) {
      eb.sendBinary(address, buff);
    }
  }

  public void testReply() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    Set<String> addresses = SharedData.getSet("addresses");
    for (final String address: addresses) {
      eb.sendBinary(address, buff, new Handler<Message<Buffer>>() {
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
      eb.registerBinaryHandler(address, new Handler<Message<Buffer>>() {
        boolean handled;

        public void handle(Message<Buffer> msg) {
          tu.checkContext();
          tu.azzert(!handled);
          tu.azzert(TestUtils.buffersEqual(buff, msg.body));
          int c = count.incrementAndGet();
          tu.azzert(c <= numHandlers);
          eb.unregisterBinaryHandler(address, this);
          if (c == numHandlers) {
            tu.testComplete();
          }
          handled = true;
        }
      });
    }

    eb.sendBinary(address, buff);
  }

  public void testStringMessage() {
    final String address = "testaddress";
    final String message = "oooh aaah";
    eb.registerBinaryHandler(address, new Handler<Message<String>>() {
      public void handle(Message<String> msg) {
        tu.checkContext();
        tu.azzert(message.equals(msg.body));
        eb.unregisterBinaryHandler(address, this);
        tu.testComplete();
      }
    });
    eb.sendBinary(address, message);
  }

  public void testLongMessage() {
    final String address = "testaddress";
    final Long message = 123L;
    eb.registerBinaryHandler(address, new Handler<Message<String>>() {
      public void handle(Message<String> msg) {
        tu.checkContext();
        tu.azzert(message.equals(msg.body));
        eb.unregisterBinaryHandler(address, this);
        tu.testComplete();
      }
    });
    eb.sendBinary(address, message);
  }


}
