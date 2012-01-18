package org.vertx.java.busmods.workqueue;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkQueue implements VertxApp, Handler<Message> {

  private static final Logger log = Logger.getLogger(WorkQueue.class);

  private final String address;
  private final long processTimeout;
  // LHS is typed as ArrayList to ensure high perf offset based index operations
  private Queue<String> processors = new LinkedList<>();
  private Queue<Message> messages = new LinkedList<>();
  private JsonHelper helper = new JsonHelper();
  private EventBus eb = EventBus.instance;

  public WorkQueue(final String address, long processTimeout) {
    this.address = address;
    this.processTimeout = processTimeout;
  }

  @Override
  public void start() {
    EventBus.instance.registerHandler(address, this);
  }

  @Override
  public void stop() {
    EventBus.instance.unregisterHandler(address, this);
    messages.clear();
    processors.clear();
  }

  public void handle(Message message) {
    Map<String, Object> map;
    try {
      map = helper.toJson(message);
    } catch (Exception e) {
      log.error("Invalid JSON: " + message.body.toString());
      return;
    }
    String action = (String)map.get("action");
    if (action == null) {
      log.error("action field must be specified");
      return;
    }
    switch (action) {
      case "register":
        doRegister(message, map);
        break;
      case "unregister":
        doUnregister(message, map);
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
