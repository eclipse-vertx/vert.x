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

package org.vertx.mods;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Work Queue Bus Module<p>
 * Please see the busmods manual for a full description<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkQueue extends BusModBase {

  // LHS is typed as ArrayList to ensure high perf offset based index operations
  private final Queue<String> processors = new LinkedList<>();
  private final Queue<JsonObject> messages = new LinkedList<>();

  private long processTimeout;
  private String persistorAddress;
  private String collection;
  private String address;

  /**
   * Start the busmod
   */
  public void start() {
    super.start();

    address = getMandatoryStringConfig("address");
    processTimeout = super.getOptionalLongConfig("process_timeout", 5 * 60 * 1000);
    persistorAddress = super.getOptionalStringConfig("persistor_address", null);
    collection = super.getOptionalStringConfig("collection", null);

    if (persistorAddress != null) {
      loadMessages();
    }

    Handler<Message<JsonObject>> registerHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doRegister(message);
      }
    };
    eb.registerHandler(address + ".register", registerHandler);
    Handler<Message<JsonObject>> unregisterHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doUnregister(message);
      }
    };
    eb.registerHandler(address + ".unregister", unregisterHandler);
    Handler<Message<JsonObject>> sendHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doSend(message);
      }
    };
    eb.registerHandler(address, sendHandler);
  }

  // Load all the message into memory
  // TODO - we could limit the amount we load at startup
  private void loadMessages() {
    JsonObject msg = new JsonObject().putString("action", "find").putString("collection", collection)
                                             .putObject("matcher", new JsonObject());
    eb.send(persistorAddress, msg, createLoadReplyHandler());
  }

  private void processLoadBatch(JsonArray toLoad) {
    Iterator iter = toLoad.iterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof JsonObject) {
        messages.add((JsonObject)obj);
      }
    }
    checkWork();
  }

  private Handler<Message<JsonObject>> createLoadReplyHandler() {
    return new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> reply) {
        processLoadBatch(reply.body.getArray("results"));
        if (reply.body.getString("status").equals("more-exist")) {
          // Get next batch
          reply.reply(null, createLoadReplyHandler());
        }
      }
    };
  }


  private void checkWork() {
    if (!messages.isEmpty() && !processors.isEmpty()) {
      final JsonObject message = messages.poll();
      final String address = processors.poll();
      final long timeoutID = vertx.setTimer(processTimeout, new Handler<Long>() {
        public void handle(Long id) {
          // Processor timed out - put message back on queue
          logger.warn("Processor timed out, message will be put back on queue");
          messages.add(message);
        }
      });
      eb.send(address, message, new Handler<Message<JsonObject>>() {
        public void handle(Message<JsonObject> reply) {
          vertx.cancelTimer(timeoutID);
          processors.add(address);
          if (persistorAddress != null) {
            JsonObject msg = new JsonObject().putString("action", "delete").putString("collection", collection)
                                             .putObject("matcher", message);
            eb.send(persistorAddress, msg, new Handler<Message<JsonObject>>() {
              public void handle(Message<JsonObject> reply) {
                if (!reply.body.getString("status").equals("ok"))                 {
                  logger.error("Failed to delete document from queue: " + reply.body.getString("message"));
                }
                checkWork();
              }
            });
          } else {
            checkWork();
          }
        }
      });
    }
  }

  private void doRegister(Message<JsonObject> message) {
    String processor = getMandatoryString("processor", message);
    if (processor == null) {
      return;
    }
    processors.add(processor);
    checkWork();
    sendOK(message);
  }

  private void doUnregister(Message<JsonObject> message) {
    String processor = getMandatoryString("processor", message);
    if (processor == null) {
      return;
    }
    processors.remove(processor);
    sendOK(message);
  }

  private void doSend(final Message<JsonObject> message) {
    if (persistorAddress != null) {
      JsonObject msg = new JsonObject().putString("action", "save").putString("collection", collection)
                                       .putObject("document", message.body);
      eb.send(persistorAddress, msg, new Handler<Message<JsonObject>>() {
        public void handle(Message<JsonObject> reply) {
          if (reply.body.getString("status").equals("ok")) {
            actualSend(message, message.body);
          } else {
            sendError(message, reply.body.getString("message"));
          }
        }
      });
    } else {
      actualSend(message, message.body);
    }
  }

  private void actualSend(Message<JsonObject> message, JsonObject work) {
    messages.add(work);
    //Been added to the queue so reply
    sendOK(message);
    checkWork();
  }

}
