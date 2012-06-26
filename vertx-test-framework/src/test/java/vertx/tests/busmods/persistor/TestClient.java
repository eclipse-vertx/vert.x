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

package vertx.tests.busmods.persistor;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.framework.TestClientBase;

/**
 *
 * Most of the testing is done in JS since it's so much easier to play with JSON in JS rather than Java
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private EventBus eb;

  private String persistorID;

  @Override
  public void start() {
    super.start();
    eb = vertx.eventBus();
    JsonObject config = new JsonObject();
    config.putString("address", "test.persistor");
    config.putString("db_name", "test_db");
    persistorID = container.deployVerticle("mongo-persistor", config, 1, new SimpleHandler() {
      public void handle() {
        tu.appReady();
      }
    });
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testPersistor() throws Exception {

    //First delete everything
    JsonObject json = new JsonObject().putString("collection", "testcoll")
                                      .putString("action", "delete").putObject("matcher", new JsonObject());

    eb.send("test.persistor", json, new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> reply) {
        tu.azzert("ok".equals(reply.body.getString("status")));
      }
    });

    final int numDocs = 1;
    for (int i = 0; i < numDocs; i++) {
      JsonObject doc = new JsonObject().putString("name", "joe bloggs").putNumber("age", 40).putString("cat-name", "watt");
      json = new JsonObject().putString("collection", "testcoll").putString("action", "save").putObject("document", doc);
      eb.send("test.persistor", json, new Handler<Message<JsonObject>>() {
        public void handle(Message<JsonObject> reply) {
          tu.azzert("ok".equals(reply.body.getString("status")));
        }
      });
    }

    JsonObject matcher = new JsonObject().putString("name", "joe bloggs");

    json = new JsonObject().putString("collection", "testcoll").putString("action", "find").putObject("matcher", matcher);

    eb.send("test.persistor", json, new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> reply) {
        tu.azzert("ok".equals(reply.body.getString("status")));
        JsonArray results = reply.body.getArray("results");
        tu.azzert(results.size() == numDocs);
        tu.testComplete();
      }
    });

  }

}
