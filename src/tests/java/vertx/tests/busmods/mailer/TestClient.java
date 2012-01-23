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

  private JsonHelper helper = new JsonHelper();

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testSendMultiple() throws Exception {
    final int numMails = 10;
    Handler<Message> replyHandler = new Handler<Message>() {
      int count;
      public void handle(Message message) {
        tu.checkContext();
        Map<String, Object> json = helper.toJson(message);
        tu.azzert(json.get("status").equals("ok"));
        if (++count == numMails) {
          tu.testComplete();
        }
      }
    };
    for (int i = 0; i < numMails; i++) {
      Map<String, Object> map = createBaseMessage();
      helper.sendJSON("testMailer", map, replyHandler);
    }
  }

  public void testSendWithSingleRecipient() throws Exception {
    Map<String, Object> map = new HashMap<>();
    String rec = System.getProperty("user.name") + "@localhost";
    map.put("to", rec);
    send(map, null);
  }

  public void testSendWithRecipientList() throws Exception {
    Map<String, Object> map = new HashMap<>();
    String rec = System.getProperty("user.name") + "@localhost";
    String[] recipients = new String[] { rec, rec, rec };
    map.put("to", recipients);
    send(map, null);
  }

  public void testSendWithSingleCC() throws Exception {
    Map<String, Object> map = new HashMap<>();
    String rec = System.getProperty("user.name") + "@localhost";
    map.put("to", rec);
    map.put("cc", rec);
    send(map, null);
  }

  public void testSendWithCCList() throws Exception {
    Map<String, Object> map = new HashMap<>();
    String rec = System.getProperty("user.name") + "@localhost";
    String[] recipients = new String[] { rec, rec, rec };
    map.put("cc", recipients);
    send(map, null);
  }

  public void testSendWithSingleBCC() throws Exception {
    Map<String, Object> map = new HashMap<>();
    String rec = System.getProperty("user.name") + "@localhost";
    map.put("to", rec);
    map.put("bcc", rec);
    send(map, null);
  }

  public void testSendWithBCCList() throws Exception {
    Map<String, Object> map = new HashMap<>();
    String rec = System.getProperty("user.name") + "@localhost";
    String[] recipients = new String[] { rec, rec, rec };
    map.put("bcc", recipients);
    send(map, null);
  }

  public void testInvalidSingleFrom() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("from", "wqdqwd qwdqwd qwdqwd ");
    send(map, "Invalid from");
  }

  public void testInvalidSingleRecipient() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("to", "wqdqwd qwdqwd qwdqwd ");
    send(map, "Invalid to");
  }

  public void testInvalidRecipientList() throws Exception {
    Map<String, Object> map = new HashMap<>();
    String[] recipients = new String[] { "tim@localhost", "qwdqwd qwdqw d", "qwdkiwqdqwd d" };
    map.put("to", recipients);
    send(map, "Invalid to");
  }

  private void send(Map<String, Object> overrides, final String error) throws Exception {
    Handler<Message> replyHandler = new Handler<Message>() {
      int count;
      public void handle(Message message) {
        tu.checkContext();
        Map<String, Object> json = helper.toJson(message);
        if (error == null) {
          tu.azzert(json.get("status").equals("ok"));
        } else {
          tu.azzert(json.get("status").equals("error"));
          tu.azzert(((String) json.get("message")).startsWith(error));
        }
        tu.testComplete();
      }
    };
    Map<String, Object> map = createBaseMessage();
    map.putAll(overrides);
    helper.sendJSON("testMailer", map, replyHandler);
  }

  private Map<String, Object> createBaseMessage() {
    String user = System.getProperty("user.name");
    Map<String, Object> map = new HashMap<>();
    map.put("from", user + "@localhost");
    map.put("to", user + "@localhost");
    map.put("subject", "This is a test");
    map.put("body", "This is the body\nof the mail");
    return map;
  }




}
