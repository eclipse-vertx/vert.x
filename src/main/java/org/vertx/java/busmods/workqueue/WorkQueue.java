package org.vertx.java.busmods.workqueue;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.util.LinkedList;
import java.util.Queue;

/**
 * TODO finish persistence
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkQueue extends BusModBase implements VertxApp  {

  private static final Logger log = Logger.getLogger(WorkQueue.class);

  private final long processTimeout;
  // LHS is typed as ArrayList to ensure high perf offset based index operations
  private final Queue<String> processors = new LinkedList<>();
  private final Queue<JsonObject> messages = new LinkedList<>();
  private Handler<JsonMessage> registerHandler;
  private Handler<JsonMessage> unregisterHandler;
  private Handler<JsonMessage> sendHandler;
  private String persistorAddress;
  private String collection;

  public WorkQueue(final String address, long processTimeout) {
    super(address, false);
    this.processTimeout = processTimeout;
  }

  public WorkQueue(final String address, long processTimeout, String persistorAddress,
                   String collection) {
    this(address, processTimeout);
    this.persistorAddress = persistorAddress;
    this.collection = collection;
  }

  public void start() {
    registerHandler = new Handler<JsonMessage>() {
      public void handle(JsonMessage message) {
        doRegister(message);
      }
    };
    eb.registerJsonHandler(address + ".register", registerHandler);
    unregisterHandler = new Handler<JsonMessage>() {
      public void handle(JsonMessage message) {
        doUnregister(message);
      }
    };
    eb.registerJsonHandler(address + ".unregister", unregisterHandler);
    sendHandler = new Handler<JsonMessage>() {
      public void handle(JsonMessage message) {
        doSend(message);
      }
    };
    eb.registerJsonHandler(address, sendHandler);
  }

  public void stop() {
    eb.unregisterJsonHandler(address + ".register", registerHandler);
    eb.unregisterJsonHandler(address + ".unregister", unregisterHandler);
    eb.unregisterJsonHandler(address, sendHandler);
  }

  private void checkWork() {
    if (!messages.isEmpty() && !processors.isEmpty()) {
      final JsonObject message = messages.poll();
      final String address = processors.poll();
      final long timeoutID = Vertx.instance.setTimer(processTimeout, new Handler<Long>() {
        public void handle(Long id) {
          // Processor timed out - put message back on queue
          log.warn("Processor timed out, message will be put back on queue");
          messages.add(message);
        }
      });
      eb.sendJson(address, message, new Handler<JsonMessage>() {
        public void handle(JsonMessage reply) {
          Vertx.instance.cancelTimer(timeoutID);
          processors.add(address);
          checkWork();
        }
      });

    }
  }

  private void doRegister(JsonMessage message) {
    String processor = getMandatoryString("processor", message);
    if (processor == null) {
      return;
    }
    processors.add(processor);
    checkWork();
    message.reply();
  }

  private void doUnregister(JsonMessage message) {
    String processor = getMandatoryString("processor", message);
    if (processor == null) {
      return;
    }
    processors.remove(processor);
    message.reply();
  }

  private void doSend(final JsonMessage message) {
    if (persistorAddress != null) {
      JsonObject msg = new JsonObject().putString("action", "save").putString("collection", collection)
                                       .putObject("document", message.jsonObject);
      eb.sendJson(persistorAddress, msg, new Handler<JsonMessage>() {
        public void handle(JsonMessage reply) {
          if (reply.jsonObject.getString("status").equals("ok")) {
            actualSend(message, message.jsonObject);
          } else {
            sendError(message, reply.jsonObject.getString("message"));
          }
        }
      });
    } else {
      actualSend(message, message.jsonObject);
    }
  }

  private void actualSend(JsonMessage message, JsonObject work) {
    messages.add(work);
    //Been added to the queue so reply
    sendOK(message);
    checkWork();
  }

}
