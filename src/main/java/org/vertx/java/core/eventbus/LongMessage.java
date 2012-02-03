package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class LongMessage extends Message<Long> {

  private static final Logger log = Logger.getLogger(LongMessage.class);

  LongMessage(String address, Long payload) {
    super(address, payload);
  }

  public LongMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      body = readBuff.getLong(++pos);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendLong(body);
    }
  }

  protected int getBodyLength() {
    return 1 + (body == null ? 0 : 8);
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_LONG;
  }

  protected void handleReply(Long reply) {
    bus.send(replyAddress, reply);
  }

}
