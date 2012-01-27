package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DoubleMessage extends Message<Double> {

  private static final Logger log = Logger.getLogger(DoubleMessage.class);

  DoubleMessage(String address, Double payload) {
    super(address, payload);
  }

  public DoubleMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Double readBody(int pos, Buffer readBuff) {
    return readBuff.getDouble(pos);
  }

  protected void writeBody(Buffer buff) {
    buff.appendDouble(body);
  }

  protected int getBodyLength() {
    return 8;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_DOUBLE;
  }

}
