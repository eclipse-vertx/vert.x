package org.vertx.java.core.eventbus;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BufferMessage extends Message<Buffer> {

  private static final Logger log = Logger.getLogger(BufferMessage.class);

  BufferMessage(String address, Buffer payload) {
    super(address, payload);
  }

  public BufferMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected Buffer readBody(int pos, Buffer readBuff) {
    int buffLength = readBuff.getInt(pos);
    pos += 4;
    byte[] payload = readBuff.getBytes(pos, pos + buffLength);
    return Buffer.create(payload);
  }

  protected void writeBody(Buffer buff) {
   buff.appendInt(body.length());
   buff.appendBuffer(body);
  }

  protected int getBodyLength() {
    return 4 + body.length();
  }

  protected Message copy() {
    BufferMessage copied = new BufferMessage(address, body.copy());
    copied.replyAddress = this.replyAddress;
    copied.bus = this.bus;
    copied.sender = this.sender;
    return copied;
  }

  protected byte type() {
    return TYPE_BUFFER;
  }

}
