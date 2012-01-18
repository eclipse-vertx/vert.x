package org.vertx.java.core.eventbus;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.buffer.Buffer;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonHelper {
  private final ObjectMapper mapper = new ObjectMapper();
  private final EventBus eb = EventBus.instance;

  public void sendJSON(Map<String, Object> message) throws Exception {
    String json = mapper.writeValueAsString(message);
    Message msg = new Message((String)message.get("address"), Buffer.create(json));
    eb.send(msg);
    message.put("messageID", msg.messageID);
  }

  public Map<String, Object> toJson(Message message) throws Exception {
    Map<String, Object> json = mapper.readValue(message.body.toString(), Map.class);
    json.put("address", message.address);
    json.put("messageID", message.messageID);
    return json;
  }
}
