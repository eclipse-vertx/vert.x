package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ByteArrayMessage extends Message<byte[]> {

  private static final Logger log = Logger.getLogger(ByteArrayMessage.class);

  ByteArrayMessage(String address, byte[] payload) {
    super(address, payload);
  }

  public ByteArrayMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    int buffLength = readBuff.getInt(pos);
    pos += 4;
    body = readBuff.getBytes(pos, pos + buffLength);
  }

  protected void writeBody(Buffer buff) {
   buff.appendInt(body.length);
   buff.appendBytes(body);
  }

  protected int getBodyLength() {
    return 4 + body.length;
  }

  protected Message copy() {
    byte[] copiedBytes = new byte[body.length];
    System.arraycopy(body, 0, copiedBytes, 0, copiedBytes.length);
    ByteArrayMessage copied = new ByteArrayMessage(address, copiedBytes);
    copied.replyAddress = this.replyAddress;
    copied.bus = this.bus;
    copied.sender = this.sender;
    return copied;
  }

  protected byte type() {
    return TYPE_BYTEARRAY;
  }

  protected void handleReply(byte[] reply) {
    bus.send(replyAddress, reply);
  }

}
