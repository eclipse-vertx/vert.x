package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class BooleanMessage extends Message<Boolean> {

  private static final Logger log = Logger.getLogger(BooleanMessage.class);

  BooleanMessage(String address, Boolean payload) {
    super(address, payload);
  }

  public BooleanMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      body = new Boolean(readBuff.getByte(pos + 1) == (byte)1);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendByte(body ? (byte)1 : (byte)0);
    }
  }

  protected int getBodyLength() {
    return 1 + (body == null ? 0 : 1);
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_BOOLEAN;
  }

  protected void handleReply(Boolean reply) {
    bus.send(replyAddress, reply);
  }

}
