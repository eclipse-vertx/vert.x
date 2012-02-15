package org.vertx.java.core.eventbus;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class JsonMessage extends Message<JsonObject> {

  private static final Logger log = LoggerFactory.getLogger(JsonMessage.class);

  private byte[] encoded;

  JsonMessage(String address, JsonObject payload) {
    super(address, payload);
  }

  public JsonMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      pos++;
      int strLength = readBuff.getInt(pos);
      pos += 4;
      byte[] bytes = readBuff.getBytes(pos, pos + strLength);
      String str = new String(bytes, CharsetUtil.UTF_8);
      body = new JsonObject(str);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendInt(encoded.length);
      buff.appendBytes(encoded);
    }
  }

  protected int getBodyLength() {
    if (body == null) {
      return 1;
    } else {
      String strJson = body.encode();
      encoded = strJson.getBytes(CharsetUtil.UTF_8);
      return 1 + 4 + encoded.length;
    }
  }

  protected Message copy() {
    Message copied = new JsonMessage(address, body == null ? null : body.copy());
    copied.replyAddress = this.replyAddress;
    copied.bus = this.bus;
    copied.sender = this.sender;
    return copied;
  }

  protected byte type() {
    return TYPE_JSON;
  }

  protected void handleReply(JsonObject reply) {
    bus.send(replyAddress, reply);
  }

}
