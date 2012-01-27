package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LongMessage extends Message<Long> {

  private static final Logger log = Logger.getLogger(LongMessage.class);

  LongMessage(String address, Long payload) {
    super(address, payload);
  }

  public LongMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Long readBody(int pos, Buffer readBuff) {
    return readBuff.getLong(pos);
  }

  protected void writeBody(Buffer buff) {
    buff.appendLong(body);
  }

  protected int getBodyLength() {
    return 8;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_LONG;
  }

}
