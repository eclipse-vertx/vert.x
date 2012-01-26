package org.vertx.java.core.eventbus;

import org.vertx.java.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonMessage {

  final EventBus bus;
  final String replyAddress;

  public final JsonObject jsonObject;

  JsonMessage(JsonObject jsonObject, EventBus bus, String replyAddress) {
    this.jsonObject = jsonObject;
    this.bus = bus;
    this.replyAddress = replyAddress;
  }

  public void reply(JsonObject object) {
    if (bus != null && replyAddress != null) {
      if (object == null) {
        object = new JsonObject();
      }
      bus.sendJson(replyAddress, object);
    }
  }

  public void reply() {
    reply(null);
  }
}
