package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class BufferMessage extends Message<Buffer> {

  private static final Logger log = Logger.getLogger(BufferMessage.class);

  BufferMessage(String address, Buffer payload) {
    super(address, payload);
  }

  public BufferMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      pos++;
      int buffLength = readBuff.getInt(pos);
      pos += 4;
      byte[] payload = readBuff.getBytes(pos, pos + buffLength);
      body = Buffer.create(payload);
    }
  }

  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendInt(body.length());
      buff.appendBuffer(body);
    }
  }

  protected int getBodyLength() {
    return 1 + (body == null ? 0 : 4 + body.length());
  }

  protected Message copy() {
    if (body == null) {
      return this;
    } else {
      BufferMessage copied = new BufferMessage(address, body.copy());
      copied.replyAddress = this.replyAddress;
      copied.bus = this.bus;
      copied.sender = this.sender;
      return copied;
    }
  }

  protected byte type() {
    return TYPE_BUFFER;
  }

  protected void handleReply(Buffer reply) {
    bus.send(replyAddress, reply);
  }

}
