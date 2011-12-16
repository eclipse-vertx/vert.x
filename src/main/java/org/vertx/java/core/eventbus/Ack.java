package org.vertx.java.core.eventbus;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Ack {//extends Sendable {

//  private static final Logger log = Logger.getLogger(Ack.class);
//
//  final String messageID;
//
//  Ack(String messageID) {
//    this.messageID = messageID;
//  }
//
//  Ack(Buffer readBuff) {
//    int messageIDLength = readBuff.getInt(1);
//    byte[] messageIDBytes = readBuff.getBytes(5, 5 + messageIDLength);
//    messageID = new String(messageIDBytes, CharsetUtil.UTF_8);
//  }
//
//  void write(NetSocket socket) {
//    int length = 1 + 4 + 4 + messageID.length();
//    Buffer totBuff = Buffer.create(length);
//    totBuff.appendInt(0);
//    totBuff.appendByte(Sendable.TYPE_ACK);
//    writeString(totBuff, messageID);
//    totBuff.setInt(0, totBuff.length() - 4);
//    socket.write(totBuff);
//  }
//
//  byte type() {
//    return Sendable.TYPE_ACK;
//  }
}
