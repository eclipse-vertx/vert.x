package org.vertx.java.busmods.workqueue;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkQueue extends BusModBase implements VertxApp  {

  private static final Logger log = Logger.getLogger(WorkQueue.class);

  private final long processTimeout;
  // LHS is typed as ArrayList to ensure high perf offset based index operations
  private final Queue<String> processors = new LinkedList<>();
  private final Queue<Map<String, Object>> messages = new LinkedList<>();
  private Handler<Message> registerHandler;
  private Handler<Message> unregisterHandler;
  private Handler<Message> sendHandler;
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
    registerHandler = new Handler<Message>() {
      public void handle(Message message) {
        Map<String, Object> json = helper.toJson(message);
        doRegister(message, json);
      }
    };
    eb.registerHandler(address + ".register", registerHandler);
    unregisterHandler = new Handler<Message>() {
      public void handle(Message message) {
        Map<String, Object> json = helper.toJson(message);
        doUnregister(message, json);
      }
    };
    eb.registerHandler(address + ".unregister", unregisterHandler);
    sendHandler = new Handler<Message>() {
      public void handle(Message message) {
        Map<String, Object> json = helper.toJson(message);
        doSend(message, json);
      }
    };
    eb.registerHandler(address, sendHandler);
  }

  public void stop() {
    eb.unregisterHandler(address, registerHandler);
    eb.unregisterHandler(address, unregisterHandler);
    eb.unregisterHandler(address, sendHandler);
  }

  private void checkWork() {
    if (!messages.isEmpty() && !processors.isEmpty()) {
      final Map<String, Object> message = messages.poll();
      final String address = processors.poll();
      final long timeoutID = Vertx.instance.setTimer(processTimeout, new Handler<Long>() {
        public void handle(Long id) {
          // Processor timed out - put message back on queue
          log.warn("Processor timed out, message will be put back on queue");
          messages.add(message);
        }
      });
      helper.sendJSON(address, message, new Handler<Message>() {
        public void handle(Message reply) {
          Vertx.instance.cancelTimer(timeoutID);
          processors.add(address);
          checkWork();
        }
      });

    }
  }

  private void doRegister(Message message, Map<String, Object> map) {
    String processor = (String)getMandatory("processor", message, map);
    if (processor == null) {
      return;
    }
    processors.add(processor);
    checkWork();
    message.reply();
  }

  private void doUnregister(Message message, Map<String, Object> map) {
    String processor = (String)getMandatory("processor", message, map);
    if (processor == null) {
      return;
    }
    processors.remove(processor);
    message.reply();
  }

  private void doSend(final Message message, final Map<String, Object> work) {
    log.info("in work queue, persistoradress = " + persistorAddress);
    if (persistorAddress != null) {
      Map<String, Object> msg = new HashMap<>();
      msg.put("action", "save");
      msg.put("collection", collection);
      msg.put("document", work);
      helper.sendJSON(persistorAddress, msg, new Handler<Message>() {
        public void handle(Message reply) {
          Map<String, Object> replyJson = helper.toJson(reply);
          if (replyJson.get("status").equals("ok")) {
            actualSend(message, work);
          } else {
            sendError(message, (String)replyJson.get("message"));
          }
        }
      });
    } else {
      actualSend(message, work);
    }
  }

  private void actualSend(Message message, Map<String, Object> work) {
    messages.add(work);
    //Been added to the queue so reply
    log.info("sending ok from workqueue");
    sendOK(message);
    checkWork();
  }

}
