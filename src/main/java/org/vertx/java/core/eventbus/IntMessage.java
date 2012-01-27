package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class IntMessage extends Message<Integer> {

  private static final Logger log = Logger.getLogger(IntMessage.class);

  IntMessage(String address, Integer payload) {
    super(address, payload);
  }

  public IntMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Integer readBody(int pos, Buffer readBuff) {
    return readBuff.getInt(pos);
  }

  protected void writeBody(Buffer buff) {
    buff.appendInt(body);
  }

  protected int getBodyLength() {
    return 4;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_INT;
  }

}
