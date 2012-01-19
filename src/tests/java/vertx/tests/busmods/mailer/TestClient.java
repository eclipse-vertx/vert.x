package vertx.tests.busmods.mailer;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.newtests.TestClientBase;

import java.util.HashMap;
import java.util.Map;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private JsonHelper sender = new JsonHelper();

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  int count;

  /*
  The tests need a mail server running on localhost, port 25
  If you install sendmail, then the mail should end up in /var/mail/<username>
   */
  public void testSimple() throws Exception {

    final int numMails = 10;

    String user = System.getProperty("user.name");

    Handler<Message> replyHandler = new Handler<Message>() {
      int count;
      public void handle(Message message) {
        if (++count == numMails) {
          tu.testComplete();
        }
      }
    };

    for (int i = 0; i < numMails; i++) {
      Map<String, Object> map = new HashMap<>();
      map.put("address", "testMailer");
      map.put("from", user + "@localhost");
      map.put("to", user + "@localhost");
      map.put("subject", "This is a test");
      map.put("body", "This is the body\nof the mail");
      sender.sendJSON(map, replyHandler);
    }
  }

}
