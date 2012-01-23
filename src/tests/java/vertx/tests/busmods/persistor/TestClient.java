package vertx.tests.busmods.persistor;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.newtests.TestClientBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Most of the testing is done in JS since it's so much easier to play with JSON in JS rather than Java
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private JsonHelper helper = new JsonHelper();

  private EventBus eb = EventBus.instance;

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testPersistor() throws Exception {

    //First delete everything
    Map<String, Object> json = new HashMap<>();
    json.put("collection", "testcoll");
    json.put("action", "delete");
    json.put("matcher", new HashMap());

    helper.sendJSON("testPersistor", json, new Handler<Message>() {
      public void handle(Message reply) {
        Map<String, Object> jsonReply = helper.toJson(reply);
        tu.azzert("ok".equals(jsonReply.get("status")));
      }
    });

    final int numDocs = 1;
    for (int i = 0; i < numDocs; i++) {
      Map<String, Object> doc = new HashMap<>();
      doc.put("name", "joe bloggs");
      doc.put("age", 40);
      doc.put("cat-name", "watt");

      json = new HashMap<>();
      json.put("collection", "testcoll");
      json.put("action", "save");
      json.put("document", doc);

      helper.sendJSON("testPersistor", json, new Handler<Message>() {
        public void handle(Message reply) {
          Map<String, Object> jsonReply = helper.toJson(reply);
          tu.azzert("ok".equals(jsonReply.get("status")));
        }
      });
    }

    Map<String, Object> matcher = new HashMap<>();
      matcher.put("name", "joe bloggs");

    json = new HashMap<>();
    json.put("collection", "testcoll");
    json.put("action", "find");
    json.put("matcher", matcher);

    helper.sendJSON("testPersistor", json, new Handler<Message>() {
      public void handle(Message reply) {
        Map<String, Object> jsonReply = helper.toJson(reply);
        tu.azzert("ok".equals(jsonReply.get("status")));
        List results = (List)jsonReply.get("results");
        tu.azzert(results.size() == numDocs);
        tu.testComplete();
      }
    });


  }

}
