package org.vertx.java.core.eventbus;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonHelper {

  private static final Logger log = Logger.getLogger(JsonHelper.class);

  private final ObjectMapper mapper = new ObjectMapper();
  private final EventBus eb = EventBus.instance;

  public void sendJSON(String address, Map<String, Object> message) {
    sendJSON(address, message, null);
  }

  public void sendJSON(String address, Map<String, Object> message, Handler<Message> replyHandler) {
    String json;
    try {
      json = mapper.writeValueAsString(message);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
    Message msg = new Message(address, Buffer.create(json));
    eb.send(msg, replyHandler);
  }

  public void sendReply(Message message, Map<String, Object> reply) {
    String json;
    try {
      json = mapper.writeValueAsString(reply);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
    message.reply(Buffer.create(json));
  }

  public Map<String, Object> toJson(Message message) {
    Map<String, Object> json = stringToJson(message.body.toString());
    return json;
  }

  public String jsonToString(Object json) {
    try {
      return mapper.writeValueAsString(json);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public Map<String, Object> stringToJson(String str) {
    try {
      return mapper.readValue(str, Map.class);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }
}
