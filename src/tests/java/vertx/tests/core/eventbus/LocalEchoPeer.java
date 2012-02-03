package vertx.tests.core.eventbus;

import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalEchoPeer extends EventBusAppBase {

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

  public void testEchoStringInitialise() {
    echoInitialise();
  }

  public void testEchoNullStringInitialise() {
    echoInitialise();
  }

  public void testEchoBooleanTrueInitialise() {
    echoInitialise();
  }

  public void testEchoBooleanFalseInitialise() {
    echoInitialise();
  }

  public void testEchoNullBooleanInitialise() {
    echoInitialise();
  }

  public void testEchoBufferInitialise() {
    echoInitialise();
  }

  public void testEchoNullBufferInitialise() {
    echoInitialise();
  }

  public void testEchoByteArrayInitialise() {
    echoInitialise();
  }

  public void testEchoNullByteArrayInitialise() {
    echoInitialise();
  }

  public void testEchoByteInitialise() {
    echoInitialise();
  }

  public void testEchoNullByteInitialise() {
    echoInitialise();
  }

  public void testEchoCharacterInitialise() {
    echoInitialise();
  }

  public void testEchoNullCharacterInitialise() {
    echoInitialise();
  }

  public void testEchoDoubleInitialise() {
    echoInitialise();
  }

  public void testEchoNullDoubleInitialise() {
    echoInitialise();
  }

  public void testEchoFloatInitialise() {
    echoInitialise();
  }

  public void testEchoNullFloatInitialise() {
    echoInitialise();
  }

  public void testEchoIntInitialise() {
    echoInitialise();
  }

  public void testEchoNullIntInitialise() {
    echoInitialise();
  }

  public void testEchoJsonInitialise() {
    echoInitialise();
  }

  public void testEchoNullJsonInitialise() {
    echoInitialise();
  }

  public void testEchoLongInitialise() {
    echoInitialise();
  }

  public void testEchoNullLongInitialise() {
    echoInitialise();
  }

  public void testEchoShortInitialise() {
    echoInitialise();
  }

  public void testEchoNullShortInitialise() {
    echoInitialise();
  }

  private void echoInitialise() {
    eb.registerHandler(LocalEchoClient.ECHO_ADDRESS, new Handler<Message>() {
          boolean handled = false;

          public void handle(Message msg) {
            tu.checkContext();
            tu.azzert(!handled);
            eb.unregisterHandler(LocalEchoClient.ECHO_ADDRESS, this);
            handled = true;
            msg.reply(msg.body);
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
