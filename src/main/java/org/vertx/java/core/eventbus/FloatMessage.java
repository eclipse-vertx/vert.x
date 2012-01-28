package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FloatMessage extends Message<Float> {

  private static final Logger log = Logger.getLogger(FloatMessage.class);

  FloatMessage(String address, Float payload) {
    super(address, payload);
  }

  public FloatMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Float readBody(int pos, Buffer readBuff) {
    return readBuff.getFloat(pos);
  }

  protected void writeBody(Buffer buff) {
    buff.appendFloat(body);
  }

  protected int getBodyLength() {
    return 4;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_FLOAT;
  }

  protected void handleReply(Float reply) {
    EventBus.instance.send(replyAddress, reply);
  }

}
