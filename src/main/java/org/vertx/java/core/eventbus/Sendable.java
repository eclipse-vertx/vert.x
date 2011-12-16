package org.vertx.java.core.eventbus;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class Sendable {

  abstract void write(NetSocket socket);

  abstract byte type();

  static final byte TYPE_MESSAGE = 1;
  static final byte TYPE_ACK = 2;

  static Sendable read(Buffer buff) {
    byte type = buff.getByte(0);
    switch (type) {
      case TYPE_MESSAGE:
        return new Message(buff);
      default:
        throw new IllegalStateException("Invalid type " + type);
    }
  }

  protected void writeString(Buffer buff, String str) {
    byte[] strBytes = str.getBytes(CharsetUtil.UTF_8);
    buff.appendInt(strBytes.length);
    buff.appendBytes(strBytes);
  }

}
