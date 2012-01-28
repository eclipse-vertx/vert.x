package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ByteMessage extends Message<Byte> {

  private static final Logger log = Logger.getLogger(ByteMessage.class);

  ByteMessage(String address, Byte payload) {
    super(address, payload);
  }

  public ByteMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Byte readBody(int pos, Buffer readBuff) {
    return readBuff.getByte(pos);
  }

  protected void writeBody(Buffer buff) {
    buff.appendByte(body);
  }

  protected int getBodyLength() {
    return 1;
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_BYTE;
  }

  protected void handleReply(Byte reply) {
    EventBus.instance.send(replyAddress, reply);
  }

}
