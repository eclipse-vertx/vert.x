package vertx.tests.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalClient extends EventBusAppBase {

  private static final String ADDRESS = "testaddress";

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
    Set<String> addresses = SharedData.getSet("addresses");
    for (String address: addresses) {
      eb.send(address, buff);
    }
  }

  public void testReply() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    data.put("buffer", buff);
    Set<String> addresses = SharedData.getSet("addresses");
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

  public void testEchoString() {
    String msg = TestUtils.randomUnicodeString(1000);
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoNullString() {
    String msg = null;
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoLong() {
    Long msg = new Random().nextLong();
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoNullLong() {
    Long msg = null;
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoInt() {
    Integer msg = new Random().nextInt();
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoNullInt() {
    Integer msg = null;
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoShort() {
    Short msg = (short)(new Random().nextInt(Short.MAX_VALUE));
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoNullShort() {
    Short msg = null;
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoByte() {
    Byte msg = (byte)(new Random().nextInt(Byte.MAX_VALUE));
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoNullByte() {
    Byte msg = null;
    echo(msg);
    eb.send(ADDRESS, msg);
  }

  public void testEchoBooleanTrue() {
    Boolean tru = Boolean.TRUE;
    echo(tru);
    eb.send(ADDRESS, tru);
  }

  public void testEchoBooleanFalse() {
    Boolean fal = Boolean.FALSE;
    echo(fal);
    eb.send(ADDRESS, fal);
  }

  public void testEchoNullBoolean() {
    Boolean fal = null;
    echo(fal);
    eb.send(ADDRESS, fal);
  }

  public void testEchoByteArray() {
    byte[] bytes = TestUtils.generateRandomByteArray(1000);
    echo(bytes);
    eb.send(ADDRESS, bytes);
  }

  public void testEchoNullByteArray() {
    byte[] bytes = null;
    echo(bytes);
    eb.send(ADDRESS, bytes);
  }

  public void testEchoFloat() {
    Float fl = (float)(new Random().nextInt() / 37);
    echo(fl);
    eb.send(ADDRESS, fl);
  }

  public void testEchoNullFloat() {
    Float fl = null;
    echo(fl);
    eb.send(ADDRESS, fl);
  }

  public void testEchoDouble() {
    Double db = (double)(new Random().nextInt() / 37);
    echo(db);
    eb.send(ADDRESS, db);
  }

  public void testEchoNullDouble() {
    Double db = null;
    echo(db);
    eb.send(ADDRESS, db);
  }

  public void testEchoBuffer() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    echo(buff);
    eb.send(ADDRESS, buff);
  }

  public void testEchoNullBuffer() {
    Buffer buff = null;
    echo(buff);
    eb.send(ADDRESS, buff);
  }

  public void testEchoJson() {
    JsonObject obj = new JsonObject();
    obj.putString("foo", "bar");
    obj.putNumber("num", 12124);
    obj.putBoolean("x", true);
    obj.putBoolean("y", false);
    echo(obj);
    eb.send(ADDRESS, obj);
  }

  public void testEchoNullJson() {
    JsonObject obj = null;
    echo(obj);
    eb.send(ADDRESS, obj);
  }

  public void testEchoCharacter() {
    Character chr = (char)(new Random().nextInt());
    echo(chr);
    eb.send(ADDRESS, chr);
  }

  public void testEchoNullCharacter() {
    Character chr = null;
    echo(chr);
    eb.send(ADDRESS, chr);
  }

  private void echo(final Object msg) {
    eb.registerHandler(ADDRESS, new Handler<Message>() {
      public void handle(Message reply) {
        tu.checkContext();
        if (msg == null) {
          tu.azzert(reply.body == null);
        } else {
          if (!(msg instanceof byte[])) {
            tu.azzert(msg.equals(reply.body), "Expecting " + msg + " got " + reply.body);
          } else {
            TestUtils.byteArraysEqual((byte[])msg, (byte[])reply.body);
          }
          if ((msg instanceof Buffer) || (msg instanceof byte[]) || (msg instanceof JsonObject)) {
            // Should be copied
            tu.azzert(msg != reply.body);
          } else {
            // Shouldn't be copied
            tu.azzert(msg == reply.body);
          }
        }
        eb.unregisterHandler(ADDRESS, this);
        tu.testComplete();
      }
    });

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
