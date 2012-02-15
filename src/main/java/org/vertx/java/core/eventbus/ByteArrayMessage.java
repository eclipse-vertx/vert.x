package org.vertx.java.core.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ByteArrayMessage extends Message<byte[]> {

  private static final Logger log = LoggerFactory.getLogger(ByteArrayMessage.class);

  ByteArrayMessage(String address, byte[] payload) {
    super(address, payload);
  }

  public ByteArrayMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      pos++;
      int buffLength = readBuff.getInt(pos);
      pos += 4;
      body = readBuff.getBytes(pos, pos + buffLength);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendInt(body.length);
      buff.appendBytes(body);
    }
  }

  protected int getBodyLength() {
    return body == null ? 1 : 1 + 4 + body.length;
  }

  protected Message copy() {
    byte[] bod;
    if (body != null) {
      bod = new byte[body.length];
      System.arraycopy(body, 0, bod, 0, bod.length);
    } else {
      bod = null;
    }
    ByteArrayMessage copied = new ByteArrayMessage(address, bod);
    copied.replyAddress = this.replyAddress;
    copied.bus = this.bus;
    copied.sender = this.sender;
    return copied;
  }

  protected byte type() {
    return TYPE_BYTEARRAY;
  }

  protected void handleReply(byte[] reply, Handler<Message<byte[]>> replyHandler) {
    bus.send(replyAddress, reply, replyHandler);
  }

}
