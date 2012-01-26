package org.vertx.java.busmods;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BusModBase {

  private static final Logger log = Logger.getLogger(BusModBase.class);

  protected final String address;
  protected final EventBus eb = EventBus.instance;

  protected BusModBase(final String address, final boolean worker) {
    if (worker && Vertx.instance.isEventLoop()) {
      throw new IllegalStateException("Worker busmod can only be created inside a worker application (user -worker when deploying");
    }
    this.address = address;
  }

  protected void sendOK(JsonMessage message) {
    sendOK(message, null);
  }

  protected void sendStatus(String status, JsonMessage message) {
    sendStatus(status, message, null);
  }

  protected void sendStatus(String status, JsonMessage message, JsonObject json) {
    if (json == null) {
      json = new JsonObject();
    }
    json.putString("status", status);
    message.reply(json);
  }

  protected void sendOK(JsonMessage message, JsonObject json) {
    sendStatus("ok", message, json);
  }

  protected void sendError(JsonMessage message, String error) {
    sendError(message, error, null);
  }

  protected void sendError(JsonMessage message, String error, Exception e) {
    log.error(error, e);
    JsonObject json = new JsonObject().putString("status", "error").putString("message", error);
    message.reply(json);
  }

  protected String getMandatoryString(String field, JsonMessage message) {
    String val = message.jsonObject.getString(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }

  protected JsonObject getMandatoryObject(String field, JsonMessage message) {
    JsonObject val = message.jsonObject.getObject(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }
}
