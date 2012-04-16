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

package vertx.tests.busmods.workqueue;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.framework.TestClientBase;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private EventBus eb;

  @Override
  public void start() {
    super.start();
    eb = vertx.eventBus();
    JsonObject config = new JsonObject();
    config.putString("address", "test.orderQueue");
    container.deployVerticle("work-queue", config, 1, new SimpleHandler() {
      public void handle() {
        tu.appReady();
      }
    });
  }

  @Override
  public void stop() {
    super.stop();
  }

  int count;

  public void test1() throws Exception {

    final int numMessages = 30;

    eb.registerHandler("done", new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        if (++count == numMessages) {
          eb.unregisterHandler("done", this);
          tu.testComplete();
        }
      }
    });

    for (int i = 0; i < numMessages; i++) {
      JsonObject obj = new JsonObject().putString("blah", "wibble" + i);
      eb.send("test.orderQueue", obj);
    }
  }

}
