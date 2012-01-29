package org.vertx.java.core.eventbus;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class StringMessage extends Message<String> {

  private static final Logger log = Logger.getLogger(StringMessage.class);

  private byte[] encoded;

  StringMessage(String address, String payload) {
    super(address, payload);
  }

  public StringMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      int strLength = readBuff.getInt(pos);
      pos += 4;
      byte[] bytes = readBuff.getBytes(pos, pos + strLength);
      body = new String(bytes, CharsetUtil.UTF_8);
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
    encoded = null;
  }

  protected int getBodyLength() {
    if (body == null) {
      return 1;
    } else {
      encoded = body.getBytes(CharsetUtil.UTF_8);
      return 1 +4 + encoded.length;
    }
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_STRING;
  }

  protected void handleReply(String reply) {
    bus.send(replyAddress, reply);
  }

}
