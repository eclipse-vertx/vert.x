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

package org.vertx.java.examples.webapp;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.deploy.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OrderMgr extends Verticle {

  private EventBus eb;
  private Logger log;

  public void start() throws Exception {
    eb = vertx.eventBus();
    log = container.getLogger();

    eb.registerHandler("demo.orderMgr", new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        validateUser(message);
      }
    });
  }

  private void validateUser(final Message<JsonObject> message) {
    JsonObject validateMessage =
        new JsonObject().putString("sessionID", message.body.getString("sessionID"));
    eb.send("demo.authMgr.validate", validateMessage, new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> reply) {
        if (reply.body.getString("status").equals("ok")) {
          message.body.putString("username", reply.body.getString("username"));
          saveOrder(message);
        } else {
          log.error("Failed to validate user");
        }
      }
    });
  }

  private void saveOrder(final Message<JsonObject> message) {
    JsonObject saveMessage = new JsonObject().putString("action", "save").
                                              putString("collection", "orders").
                                              putObject("document", message.body);
    eb.send("demo.persistor", saveMessage, new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> reply) {
        if (reply.body.getString("status").equals("ok")) {
          log.info("Order successfully processed");
          message.reply(new JsonObject().putString("status", "ok"));
        } else {
          log.error("Failed to save user");
        }
      }
    });
  }
}