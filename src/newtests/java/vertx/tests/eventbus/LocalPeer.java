package vertx.tests.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalPeer extends EventBusAppBase {

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

  public void testPubSubInitialise() {
    final String address = "some-address";
    eb.registerHandler(address, new Handler<Message>() {
      boolean handled = false;
      public void handle(Message msg) {
        check.check();
        tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body));
        tu.azzert(address.equals(msg.address));
        tu.azzert(msg.messageID != null);
        eb.unregisterHandler("some-address", this);
        handled = true;
        tu.testComplete();
      }
    });
    tu.testComplete();
  }

  public void testPubSubMultipleHandlersInitialise() {

    final String address2 = "some-other-address";
    final Handler<Message> otherHandler = new Handler<Message>() {
      public void handle(Message msg) {
        tu.azzert(false, "Should not receive message");
      }
    };
    eb.registerHandler(address2, otherHandler);

    final String address = "some-address";
    eb.registerHandler(address, new Handler<Message>() {
      boolean handled = false;

      public void handle(Message msg) {
        check.check();
        tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body));
        tu.azzert(address.equals(msg.address));
        tu.azzert(msg.messageID != null);
        eb.unregisterHandler(address, this);
        eb.unregisterHandler(address, otherHandler);
        handled = true;
        tu.testComplete();
      }
    });

    tu.testComplete();
  }

  public void testNoBufferInitialise() {
    final String address = "some-address";
    eb.registerHandler("some-address", new Handler<Message>() {
      boolean handled = false;
      public void handle(Message msg) {
        check.check();
        tu.azzert(msg.body.length() == 0);
        tu.azzert(address.equals(msg.address));
        tu.azzert(msg.messageID != null);
        eb.unregisterHandler("some-address", this);
        handled = true;
        tu.testComplete();
      }
    });
    tu.testComplete();
  }

  public void testNullBufferInitialise() {
    final String address = "some-address";
    eb.registerHandler("some-address", new Handler<Message>() {
      boolean handled = false;
      public void handle(Message msg) {
        check.check();
        tu.azzert(msg.body.length() == 0);
        tu.azzert(address.equals(msg.address));
        tu.azzert(msg.messageID != null);
        eb.unregisterHandler("some-address", this);
        handled = true;
        tu.testComplete();
      }
    });
    tu.testComplete();
  }

  public void testPointToPointInitialise() {
    final String address = UUID.randomUUID().toString();
    eb.registerHandler(address, new Handler<Message>() {
      boolean handled = false;
      public void handle(Message msg) {
        check.check();
        tu.azzert(!handled);
        tu.azzert(TestUtils.buffersEqual((Buffer)data.get("buffer"), msg.body));
        tu.azzert(address.equals(msg.address));
        tu.azzert(msg.messageID != null);
        eb.unregisterHandler(address, this);
        handled = true;
        tu.testComplete();
      }
    });
    SharedData.getSet("addresses").add(address);
    tu.testComplete();
  }

  public void testReplyInitialise() {
    final String address = UUID.randomUUID().toString();
    eb.registerHandler(address, new Handler<Message>() {
      boolean handled = false;
      public void handle(Message msg) {
        check.check();
        tu.azzert(!handled);
        tu.azzert(TestUtils.buffersEqual((Buffer)data.get("buffer"), msg.body));
        tu.azzert(address.equals(msg.address));
        tu.azzert(msg.messageID != null);
        eb.unregisterHandler(address, this);
        handled = true;
        msg.reply(Buffer.create("reply" + address));
      }
    });
    SharedData.getSet("addresses").add(address);
    tu.testComplete();
  }




}
