package org.vertx.java.core.cluster;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Ack extends Sendable {

  final String messageID;

  Ack(String messageID) {
    this.messageID = messageID;
  }

  Ack(Buffer readBuff) {
    int messageIDLength = readBuff.getInt(0);
    byte[] messageIDBytes = readBuff.getBytes(4, 4 + messageIDLength);
    messageID = new String(messageIDBytes, CharsetUtil.UTF_8);
  }

  void write(NetSocket socket) {
    int length = 1 + 4 + messageID.length();
    Buffer totBuff = Buffer.create(length);
    totBuff.appendInt(0);
    totBuff.appendByte(Sendable.TYPE_ACK);
    writeString(totBuff, messageID);
  }

  byte type() {
    return Sendable.TYPE_ACK;
  }
}
