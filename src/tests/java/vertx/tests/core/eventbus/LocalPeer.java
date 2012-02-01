package vertx.tests.core.eventbus;

import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;

import java.util.Set;
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
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          boolean handled = false;

          public void handle(Message<Buffer> msg) {
            tu.checkContext();
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body));
            handled = true;
            eb.unregisterHandler("some-address", this, new CompletionHandler<Void>() {
              public void handle(Future<Void> event) {
                if (event.succeeded()) {
                  tu.testComplete();
                } else {
                  tu.azzert(false, "Failed to unregister");
                }
              }
            });
          }
        }, new CompletionHandler<Void>() {
      public void handle(Future<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );

  }

  public void testPubSubMultipleHandlersInitialise() {

    final String address2 = "some-other-address";
    final Handler<Message<Buffer>> otherHandler = new Handler<Message<Buffer>>() {
      public void handle(Message msg) {
        tu.azzert(false, "Should not receive message");
      }
    };
    eb.registerHandler(address2, otherHandler);

    final String address = "some-address";
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          boolean handled = false;

          public void handle(Message<Buffer> msg) {
            tu.checkContext();
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body));
            eb.unregisterHandler(address, this, new CompletionHandler<Void>() {
              public void handle(Future<Void> event) {
                if (event.succeeded()) {
                  tu.testComplete();
                } else {
                  tu.azzert(false, "Failed to unregister");
                }
              }
            });
            eb.unregisterHandler(address, otherHandler);
            handled = true;
          }
        }, new CompletionHandler<Void>() {
      public void handle(Future<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }

  public void testPointToPointInitialise() {
    final String address = UUID.randomUUID().toString();
    SharedData.instance.getSet("addresses").add(address);
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          boolean handled = false;
          public void handle(Message<Buffer> msg) {
            tu.checkContext();
            tu.azzert(!handled);
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body));
            eb.unregisterHandler(address, this, new CompletionHandler<Void>() {
              public void handle(Future<Void> event) {
                if (event.succeeded()) {
                  tu.testComplete();
                } else {
                  tu.azzert(false, "Failed to unregister");
                }
              }
            });
            handled = true;
          }
        }, new CompletionHandler<Void>() {
      public void handle(Future<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }

  public void testReplyInitialise() {
    final String address = UUID.randomUUID().toString();
    Set<String> addresses = SharedData.instance.getSet("addresses");
    addresses.add(address);
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          boolean handled = false;

          public void handle(Message<Buffer> msg) {
            tu.checkContext();
            tu.azzert(!handled);
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body));
            eb.unregisterHandler(address, this);
            handled = true;
            msg.reply(Buffer.create("reply" + address));
          }
        }, new CompletionHandler<Void>() {
      public void handle(Future<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }

  public void testEchoStringInitialise() {
    echoInitialise();
  }

  public void testEchoBooleanTrueInitialise() {
    echoInitialise();
  }

  public void testEchoBooleanFalseInitialise() {
    echoInitialise();
  }

  public void testEchoBufferInitialise() {
    echoInitialise();
  }

  public void testEchoByteArrayInitialise() {
    echoInitialise();
  }

  public void testEchoCharacterInitialise() {
    echoInitialise();
  }

  public void testEchoDoubleInitialise() {
    echoInitialise();
  }

  public void testEchoFloatInitialise() {
    echoInitialise();
  }

  public void testEchoIntInitialise() {
    echoInitialise();
  }

  public void testEchoJsonInitialise() {
    echoInitialise();
  }

  public void testEchoLongInitialise() {
    echoInitialise();
  }

  public void testEchoShortInitialise() {
    echoInitialise();
  }

  private void echoInitialise() {
    final String address = UUID.randomUUID().toString();
    SharedData.instance.getSet("addresses").add(address);
    eb.registerHandler(address, new Handler<Message>() {
          boolean handled = false;
          public void handle(Message msg) {
            tu.checkContext();
            tu.azzert(!handled);
            eb.unregisterHandler(address, this);
            handled = true;
            msg.reply(msg);
          }
        }, new CompletionHandler<Void>() {
      public void handle(Future<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }




}
