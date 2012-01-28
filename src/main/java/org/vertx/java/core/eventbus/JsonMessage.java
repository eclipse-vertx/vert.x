package org.vertx.java.core.eventbus;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonMessage extends Message<JsonObject> {

  private static final Logger log = Logger.getLogger(JsonMessage.class);

  private byte[] encoded;

  JsonMessage(String address, JsonObject payload) {
    super(address, payload);
  }

  public JsonMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected JsonObject readBody(int pos, Buffer readBuff) {
    int strLength = readBuff.getInt(pos);
    pos += 4;
    byte[] bytes = readBuff.getBytes(pos, pos + strLength);
    String str = new String(bytes, CharsetUtil.UTF_8);
    return new JsonObject(str);
  }

  protected void writeBody(Buffer buff) {
    buff.appendInt(encoded.length);
    buff.appendBytes(encoded);
    encoded = null;
  }

  protected int getBodyLength() {
    String strJson = body.encode();
    encoded = strJson.getBytes(CharsetUtil.UTF_8);
    return 4 + encoded.length;
  }

  protected Message copy() {
    return new JsonMessage(address, body.copy());
  }

  protected byte type() {
    return TYPE_JSON;
  }

  protected void handleReply(JsonObject reply) {
    EventBus.instance.send(replyAddress, reply);
  }

}
