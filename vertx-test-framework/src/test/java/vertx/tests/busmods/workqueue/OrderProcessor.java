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
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.framework.TestUtils;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OrderProcessor extends Verticle implements Handler<Message<JsonObject>> {

  private TestUtils tu;

  private EventBus eb;

  private String address = UUID.randomUUID().toString();

  @Override
  public void start() throws Exception {
    eb = vertx.eventBus();
    tu = new TestUtils(vertx);
    eb.registerHandler(address, this);
    JsonObject msg = new JsonObject().putString("processor", address);
    eb.send("test.orderQueue.register", msg);
    tu.appReady();
  }


  @Override
  public void stop() throws Exception {

    JsonObject msg = new JsonObject().putString("processor", address);
    eb.send("test.orderQueue.unregister", msg);

    eb.unregisterHandler(address, this);

    tu.appStopped();
  }

  public void handle(Message<JsonObject> message) {
    try {
      // Simulate some processing time - ok to sleep here since this is a worker application
      Thread.sleep(100);

      message.reply();
      eb.send("done", new JsonObject());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
