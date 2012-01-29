package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class CharacterMessage extends Message<Character> {

  private static final Logger log = Logger.getLogger(CharacterMessage.class);

  CharacterMessage(String address, Character payload) {
    super(address, payload);
  }

  public CharacterMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      body = (char)readBuff.getShort(pos);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendShort((short)body.charValue());
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
    return TYPE_CHARACTER;
  }

  protected void handleReply(Character reply) {
    bus.send(replyAddress, reply);
  }

}
