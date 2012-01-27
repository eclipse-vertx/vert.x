package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BooleanMessage extends Message<Boolean> {

  private static final Logger log = Logger.getLogger(BooleanMessage.class);

  BooleanMessage(String address, Boolean payload) {
    super(address, payload);
  }

  public BooleanMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Boolean readBody(int pos, Buffer readBuff) {
    return readBuff.getByte(pos) == (byte)1;
  }

  protected void writeBody(Buffer buff) {
    buff.appendByte(body ? (byte)1 : (byte)0);
  }

  protected int getBodyLength() {
    return 1;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_BOOLEAN;
  }

}
