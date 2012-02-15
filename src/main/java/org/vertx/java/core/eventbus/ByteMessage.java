package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ByteMessage extends Message<Byte> {

  private static final Logger log = LoggerFactory.getLogger(ByteMessage.class);

  ByteMessage(String address, Byte payload) {
    super(address, payload);
  }

  public ByteMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      body = readBuff.getByte(++pos);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendByte(body);
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
    return TYPE_BYTE;
  }

  protected void handleReply(Byte reply) {
    bus.send(replyAddress, reply);
  }

}
