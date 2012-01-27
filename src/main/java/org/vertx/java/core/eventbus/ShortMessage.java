package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ShortMessage extends Message<Short> {

  private static final Logger log = Logger.getLogger(ShortMessage.class);

  ShortMessage(String address, Short payload) {
    super(address, payload);
  }

  public ShortMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Short readBody(int pos, Buffer readBuff) {
    return readBuff.getShort(pos);
  }

  protected void writeBody(Buffer buff) {
    buff.appendShort(body);
  }

  protected int getBodyLength() {
    return 2;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_SHORT;
  }

}
