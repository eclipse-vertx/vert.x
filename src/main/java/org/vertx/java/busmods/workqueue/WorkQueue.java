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
  private Queue<String> processors = new LinkedList<>();
  private Queue<Message> messages = new LinkedList<>();

  public WorkQueue(final String address, long processTimeout) {
    super(address);
    this.processTimeout = processTimeout;
  }

  @Override
  public void stop() {
    EventBus.instance.unregisterHandler(address, this);
    super.stop();
  }

  public void handle(Message message, Map<String, Object> json) {
    String action = (String)json.get("action");
    if (action == null) {
      log.error("action field must be specified");
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
        doSend(message);
        break;
      default:
        throw new IllegalArgumentException("Invalid action: " + action);
    }
  }

  private void checkWork() {
    if (!messages.isEmpty() && !processors.isEmpty()) {
      final Message message = messages.poll();
      final String address = processors.poll();

      Message newMessage = new Message(address, message.body);
      eb.send(newMessage, new Handler<Message>() {
        public void handle(Message reply) {
          processors.add(address);
          checkWork();
        }
      });
      Vertx.instance.setTimer(processTimeout, new Handler<Long>() {
        public void handle(Long id) {
          // Processor timed out - put message back on queue
          log.warn("Processor timed out, message will be put back on queue");
          messages.add(message);
        }
      });
    }
  }

  private void doRegister(Message message, Map<String, Object> map) {
    String address = (String)map.get("processor");
    processors.add(address);
    checkWork();
    message.reply();
  }

  private void doUnregister(Message message, Map<String, Object> map) {
    String address = (String)map.get("processor");
    processors.remove(address);
    message.reply();
  }

  private void doSend(Message message) {
    messages.add(message);
    checkWork();
  }

}
