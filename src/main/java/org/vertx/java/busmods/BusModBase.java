package org.vertx.java.busmods;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BusModBase {

  private static final Logger log = Logger.getLogger(BusModBase.class);

  protected final String address;
  protected final JsonHelper helper = new JsonHelper();
  protected final EventBus eb = EventBus.instance;

  protected BusModBase(final String address, final boolean worker) {
    if (worker && Vertx.instance.isEventLoop()) {
      throw new IllegalStateException("Worker busmod can only be created inside a worker application (user -worker when deploying");
    }
    this.address = address;
  }

  protected void sendOK(Message message) {
    sendOK(message, null);
  }

  protected void sendStatus(String status, Message message) {
    sendStatus(status, message, null);
  }

  protected void sendStatus(String status, Message message, Map<String, Object> json) {
    if (json == null) {
      json = new HashMap<>();
    }
    json.put("status", status);
    helper.sendReply(message, json);
  }


  protected void sendOK(Message message, Map<String, Object> json) {
    sendStatus("ok", message, json);
  }

  protected void sendError(Message message, String error) {
    sendError(message, error, null);
  }

  protected void sendError(Message message, String error, Exception e) {
    log.error(error, e);
    Map<String, Object> json = new HashMap<>();
    json.put("status", "error");
    json.put("message", error);
    helper.sendReply(message, json);
  }

  protected Object getMandatory(String field, Message message, Map<String, Object> json) {
    Object val = json.get(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }
}
