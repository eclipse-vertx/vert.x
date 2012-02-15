package org.vertx.java.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class DoubleMessage extends Message<Double> {

  private static final Logger log = LoggerFactory.getLogger(DoubleMessage.class);

  DoubleMessage(String address, Double payload) {
    super(address, payload);
  }

  public DoubleMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      body = readBuff.getDouble(++pos);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendDouble(body);
    }
  }

  protected int getBodyLength() {
    return 1 + (body == null ? 0 : 8);
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_DOUBLE;
  }

  protected void handleReply(Double reply, Handler<Message<Double>> replyHandler) {
    bus.send(replyAddress, reply, replyHandler);
  }

}
