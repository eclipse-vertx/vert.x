/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vertx.tests.busmods.mailer;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.framework.TestClientBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    JsonObject config = new JsonObject();
    config.putString("address", "test.mailer");
    container.deployVerticle("mailer", config, 1, new SimpleHandler() {
      public void handle() {
        tu.appReady();
      }
    });
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testSendMultiple() throws Exception {
    final int numMails = 10;
    Handler<Message<JsonObject>> replyHandler = new Handler<Message<JsonObject>>() {
      int count;
      public void handle(Message<JsonObject> message) {
        tu.checkContext();
        tu.azzert(message.body.getString("status").equals("ok"));
        if (++count == numMails) {
          tu.testComplete();
        }
      }
    };
    for (int i = 0; i < numMails; i++) {
      JsonObject jsonObject = createBaseMessage();
      vertx.eventBus().send("test.mailer", jsonObject, replyHandler);
    }
  }

  public void testSendWithSingleRecipient() throws Exception {
    String rec = System.getProperty("user.name") + "@localhost";
    JsonObject jsonObject = new JsonObject().putString("to", rec);
    sendWithOverrides(jsonObject, null);
  }

  public void testSendWithRecipientList() throws Exception {
    String rec = System.getProperty("user.name") + "@localhost";
    JsonArray recipients = new JsonArray(new String[] { rec, rec, rec });
    JsonObject jsonObject = new JsonObject().putArray("to", recipients);
    sendWithOverrides(jsonObject, null);
  }

  public void testSendWithSingleCC() throws Exception {
    String rec = System.getProperty("user.name") + "@localhost";
    JsonObject jsonObject = new JsonObject().putString("to", rec).putString("cc", rec);
    sendWithOverrides(jsonObject, null);
  }

  public void testSendWithCCList() throws Exception {
    String rec = System.getProperty("user.name") + "@localhost";
    JsonArray recipients = new JsonArray(new String[] { rec, rec, rec });
    JsonObject jsonObject = new JsonObject().putArray("cc", recipients);
    sendWithOverrides(jsonObject, null);
  }

  public void testSendWithSingleBCC() throws Exception {
    String rec = System.getProperty("user.name") + "@localhost";
    JsonObject jsonObject = new JsonObject().putString("to", rec).putString("bcc", rec);
    sendWithOverrides(jsonObject, null);
  }

  public void testSendWithBCCList() throws Exception {
    String rec = System.getProperty("user.name") + "@localhost";
    JsonArray recipients = new JsonArray(new String[] { rec, rec, rec });
    JsonObject jsonObject = new JsonObject().putArray("bcc", recipients);
    sendWithOverrides(jsonObject, null);
  }

  public void testInvalidSingleFrom() throws Exception {
    JsonObject jsonObject = new JsonObject().putString("from", "wqdqwd qwdqwd qwdqwd ");
    sendWithOverrides(jsonObject, "Invalid from");
  }

  public void testInvalidSingleRecipient() throws Exception {
    JsonObject jsonObject = new JsonObject().putString("to", "wqdqwd qwdqwd qwdqwd ");
    sendWithOverrides(jsonObject, "Invalid to");
  }

  public void testInvalidRecipientList() throws Exception {
    JsonArray recipients = new JsonArray(new String[] { "tim@localhost", "qwdqwd qwdqw d", "qwdkiwqdqwd d" });
    JsonObject jsonObject = new JsonObject().putArray("to", recipients);
    sendWithOverrides(jsonObject, "Invalid to");
  }

  public void testNoSubject() throws Exception {
    JsonObject jsonObject = createBaseMessage();
    jsonObject.removeField("subject");
    send(jsonObject, "subject must be specified");
  }

  public void testNoBody() throws Exception {
    JsonObject jsonObject = createBaseMessage();
    jsonObject.removeField("body");
    send(jsonObject, "body must be specified");
  }

  public void testNoTo() throws Exception {
    JsonObject jsonObject = createBaseMessage();
    jsonObject.removeField("to");
    send(jsonObject, "to address(es) must be specified");
  }

  public void testNoFrom() throws Exception {
    JsonObject jsonObject = createBaseMessage();
    jsonObject.removeField("from");
    send(jsonObject, "from address must be specified");
  }

  private void sendWithOverrides(JsonObject overrides, final String error) throws Exception {
    Handler<Message<JsonObject>> replyHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        tu.checkContext();
        if (error == null) {
          tu.azzert(message.body.getString("status").equals("ok"));
        } else {
          tu.azzert(message.body.getString("status").equals("error"));
          tu.azzert(message.body.getString("message").startsWith(error));
        }
        tu.testComplete();
      }
    };
    JsonObject jsonObject = createBaseMessage();
    jsonObject.mergeIn(overrides);
    vertx.eventBus().send("test.mailer", jsonObject, replyHandler);
  }

  private void send(JsonObject message, final String error) throws Exception {
    Handler<Message<JsonObject>> replyHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        tu.checkContext();
        if (error == null) {
          tu.azzert(message.body.getString("status").equals("ok"));
        } else {
          tu.azzert(message.body.getString("status").equals("error"));
          tu.azzert(message.body.getString("message").startsWith(error));
        }
        tu.testComplete();
      }
    };
    vertx.eventBus().send("test.mailer", message, replyHandler);
  }

  private JsonObject createBaseMessage() {
    String user = System.getProperty("user.name");
    JsonObject jsonObject = new JsonObject().putString("from", user + "@localhost").putString("to", user + "@localhost")
        .putString("subject", "This is a test").putString("body", "This is the body\nof the mail");
    return jsonObject;
  }

}
