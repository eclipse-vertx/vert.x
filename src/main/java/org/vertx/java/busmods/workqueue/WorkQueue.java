package org.vertx.java.busmods.workqueue;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkQueue extends BusModBase {

  private static final Logger log = Logger.getLogger(WorkQueue.class);

  private final long processTimeout;
  // LHS is typed as ArrayList to ensure high perf offset based index operations
  private final Queue<String> processors = new LinkedList<>();
  private final Queue<Map<String, Object>> messages = new LinkedList<>();

  public WorkQueue(final String address, long processTimeout) {
    super(address, false);
    this.processTimeout = processTimeout;
  }

  @Override
  public void stop() {
    EventBus.instance.unregisterHandler(address, this);
    super.stop();
  }

  public void handle(Message message, Map<String, Object> json) {
    String action = (String)getMandatory("action", message, json);
    if (action == null) {
      return;
    }
    switch (action) {
      case "register":
        doRegister(message, json);
        break;
      case "unregister":
        doUnregister(message, json);
        break;
      case "send":
        doSend(message, json);
        break;
      default:
        throw new IllegalArgumentException("Invalid action: " + action);
    }
  }

  private void checkWork() {
    if (!messages.isEmpty() && !processors.isEmpty()) {
      final Map<String, Object> message = messages.poll();
      final String address = processors.poll();
      message.put("address", address);
      final long timeoutID = Vertx.instance.setTimer(processTimeout, new Handler<Long>() {
        public void handle(Long id) {
          // Processor timed out - put message back on queue
          log.warn("Processor timed out, message will be put back on queue");
          messages.add(message);
        }
      });
      helper.sendJSON(message, new Handler<Message>() {
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

  private void doSend(Message message, Map<String, Object> map) {
    Map<String, Object> work = (Map<String, Object>)getMandatory("work", message, map);
    if (work == null) {
      return;
    }
    messages.add(work);
    //Been added to the queue so reply
    sendOK(message);
    checkWork();
  }

}
