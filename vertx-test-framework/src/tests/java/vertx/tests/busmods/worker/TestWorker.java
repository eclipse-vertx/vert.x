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

package vertx.tests.busmods.worker;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.framework.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestWorker extends Verticle implements Handler<Message<JsonObject>> {

  private TestUtils tu;

  private EventBus eb;

  private String address = "testWorker";

  @Override
  public void start() throws Exception {
    eb = vertx.eventBus();
    tu = new TestUtils(vertx);
    eb.registerHandler(address, this);
    tu.appReady();
  }


  @Override
  public void stop() throws Exception {
    eb.unregisterHandler(address, this);
    tu.appStopped();
  }

  public void handle(final Message<JsonObject> message) {
    try {
      tu.azzert(message.body.getString("foo").equals("wibble"));
      tu.azzert(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));

      // Trying to create any network clients or servers should fail - workers can only use the event bus

      try {
        vertx.createNetServer();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

       try {
        vertx.createNetClient();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

       try {
        vertx.createHttpServer();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

       try {
        vertx.createHttpClient();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

      // Simulate some processing time - ok to sleep here since this is a worker application
      Thread.sleep(100);

      JsonObject reply = new JsonObject().putString("eek", "blurt");
      message.reply(reply);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
