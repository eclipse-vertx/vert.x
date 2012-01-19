package vertx.tests.busmods.mailer;

import org.vertx.java.core.Handler;
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

  public void testSimple() throws Exception {

    final int numMails = 10;

    Handler<Message> replyHandler = new Handler<Message>() {
      int count;
      public void handle(Message message) {
        tu.checkContext();
        Map<String, Object> json = sender.toJson(message);
        tu.azzert(json.get("status").equals("ok"));

        if (++count == numMails) {
          tu.testComplete();
        }
      }
    };

    String user = System.getProperty("user.name");
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

  public void testInvalidFrom() throws Exception {

    Handler<Message> replyHandler = new Handler<Message>() {
      int count;
      public void handle(Message message) {
        tu.checkContext();
        Map<String, Object> json = sender.toJson(message);
        tu.azzert(json.get("status").equals("error"));
        tu.azzert(((String) json.get("message")).startsWith("Invalid from address"));
        tu.testComplete();
      }
    };

    String user = System.getProperty("user.name");
    Map<String, Object> map = new HashMap<>();
    map.put("address", "testMailer");
    map.put("from", " dwqd qdw wdq d d");
    map.put("to", user + "@localhost");
    map.put("subject", "This is a test");
    map.put("body", "This is the body\nof the mail");
    sender.sendJSON(map, replyHandler);
  }

  public void testInvalidTo() throws Exception {

    Handler<Message> replyHandler = new Handler<Message>() {
      int count;
      public void handle(Message message) {
        tu.checkContext();
        Map<String, Object> json = sender.toJson(message);
        tu.azzert(json.get("status").equals("error"));
        tu.azzert(((String)json.get("message")).startsWith("Invalid recipients"));
        tu.testComplete();
      }
    };

    String user = System.getProperty("user.name");
    Map<String, Object> map = new HashMap<>();
    map.put("address", "testMailer");
    map.put("to", " dwqd qdw wdq d d");
    map.put("from", user + "@localhost");
    map.put("subject", "This is a test");
    map.put("body", "This is the body\nof the mail");
    sender.sendJSON(map, replyHandler);
  }



}
