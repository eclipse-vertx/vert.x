package org.vertx.java.busmods;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BusModBase {

  private static final Logger log = Logger.getLogger(BusModBase.class);

  protected final EventBus eb = EventBus.instance;
  protected JsonObject config;
  protected String address;

  protected BusModBase(boolean worker) {
    if (worker && Vertx.instance.isEventLoop()) {
      throw new IllegalStateException("Worker busmod can only be created inside a worker application (user -worker when deploying");
    }
  }

  protected void start() {
    config = Vertx.instance.getConfig();
    address = config.getString("address");
    if (address == null) {
      throw new IllegalArgumentException("address must be specified in config for busmod");
    }
  }

  protected void sendOK(Message<JsonObject> message) {
    sendOK(message, null);
  }

  protected void sendStatus(String status, Message<JsonObject> message) {
    sendStatus(status, message, null);
  }

  protected void sendStatus(String status, Message<JsonObject> message, JsonObject json) {
    if (json == null) {
      json = new JsonObject();
    }
    json.putString("status", status);
    message.reply(json);
  }

  protected void sendOK(Message<JsonObject> message, JsonObject json) {
    sendStatus("ok", message, json);
  }

  protected void sendError(Message<JsonObject> message, String error) {
    sendError(message, error, null);
  }

  protected void sendError(Message<JsonObject> message, String error, Exception e) {
    log.error(error, e);
    JsonObject json = new JsonObject().putString("status", "error").putString("message", error);
    message.reply(json);
  }

  protected String getMandatoryString(String field, Message<JsonObject> message) {
    String val = message.body.getString(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }

  protected JsonObject getMandatoryObject(String field, Message<JsonObject> message) {
    JsonObject val = message.body.getObject(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }

  protected boolean getOptionalBooleanConfig(String fieldName, boolean defaultValue) {
    Boolean b = config.getBoolean(fieldName);
    return b == null ? defaultValue : b.booleanValue();
  }

  protected String getOptionalStringConfig(String fieldName, String defaultValue) {
    String b = config.getString(fieldName);
    return b == null ? defaultValue : b;
  }

  protected int getOptionalIntConfig(String fieldName, int defaultValue) {
    Integer b = (Integer)config.getNumber(fieldName);
    return b == null ? defaultValue : b.intValue();
  }

  protected long getOptionalLongConfig(String fieldName, int defaultValue) {
    Long l = (Long)config.getNumber(fieldName);
    return l == null ? defaultValue : l.longValue();
  }

  protected String getMandatoryStringConfig(String fieldName) {
    String b = config.getString(fieldName);
    if (b == null) {
      throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
    }
    return b;
  }
}
