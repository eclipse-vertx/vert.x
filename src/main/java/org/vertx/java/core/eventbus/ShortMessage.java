package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ShortMessage extends Message<Short> {

  private static final Logger log = LoggerFactory.getLogger(ShortMessage.class);

  ShortMessage(String address, Short payload) {
    super(address, payload);
  }

  public ShortMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      body = readBuff.getShort(++pos);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendShort(body);
    }
  }

  protected int getBodyLength() {
    return 1 + (body == null ? 0 : 2);
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_SHORT;
  }

  protected void handleReply(Short reply) {
    bus.send(replyAddress, reply);
  }

}
