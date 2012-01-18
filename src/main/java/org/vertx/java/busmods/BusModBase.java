package org.vertx.java.busmods;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BusModBase implements VertxApp, Handler<Message> {

  private static final Logger log = Logger.getLogger(BusModBase.class);

  protected final String address;
  protected JsonHelper helper = new JsonHelper();
  protected EventBus eb = EventBus.instance;

  protected BusModBase(final String address) {
    this.address = address;
  }

  @Override
  public void start() {
    EventBus.instance.registerHandler(address, this);
  }

  @Override
  public void stop() {
    EventBus.instance.unregisterHandler(address, this);
  }

  @Override
  public void handle(Message message) {
    Map<String, Object> json;
    try {
      json = helper.toJson(message);
    } catch (Exception e) {
      log.error("Invalid JSON: " + message.body.toString());
      return;
    }
    handle(message, json);
  }

  public abstract void handle(Message message, Map<String, Object> json);
}
