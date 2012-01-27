package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CharacterMessage extends Message<Character> {

  private static final Logger log = Logger.getLogger(CharacterMessage.class);

  CharacterMessage(String address, Character payload) {
    super(address, payload);
  }

  public CharacterMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Character readBody(int pos, Buffer readBuff) {
    return (char)readBuff.getShort(pos);
  }

  protected void writeBody(Buffer buff) {
    buff.appendShort((short)body.charValue());
  }

  protected int getBodyLength() {
    return 2;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_CHARACTER;
  }

}
