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

  public void sendJSON(Map<String, Object> message) {
    sendJSON(message, null);
  }

  public void sendJSON(Map<String, Object> message, Handler<Message> replyHandler) {
    String json;
    try {
      json = mapper.writeValueAsString(message);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
    Message msg = new Message((String)message.get("address"), Buffer.create(json));
    eb.send(msg, replyHandler);
    message.put("messageID", msg.messageID);
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
    Map<String, Object> json;
    try {
      json = mapper.readValue(message.body.toString(), Map.class);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
    json.put("address", message.address);
    json.put("messageID", message.messageID);
    return json;
  }
}
