package org.vertx.java.core.cluster;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.ServerID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Message extends Sendable {

  private static final Logger log = Logger.getLogger(Message.class);

  public String messageID;
  public final String subName;
  ServerID sender;
  boolean requiresAck;
  public final Buffer buff;
  EventBus bus;

  public Message(String subName, Buffer buff) {
    this.subName = subName;
    this.buff = buff;
  }

  Message(Buffer readBuff) {
    // TODO Meh. This could be improved
    int pos = 1;
    int messageIDLength = readBuff.getInt(pos);
    pos += 4;
    byte[] messageIDBytes = readBuff.getBytes(pos, pos + messageIDLength);
    pos += messageIDLength;
    messageID = new String(messageIDBytes, CharsetUtil.UTF_8);

    int subNameLength = readBuff.getInt(pos);
    pos += 4;
    byte[] subNameBytes = readBuff.getBytes(pos, pos + subNameLength);
    pos += subNameLength;
    subName = new String(subNameBytes, CharsetUtil.UTF_8);

    int port = readBuff.getInt(pos);
    pos += 4;
    int hostLength = readBuff.getInt(pos);
    pos += 4;
    byte[] hostBytes = readBuff.getBytes(pos, pos + hostLength);
    pos += hostLength;
    String host = new String(hostBytes, CharsetUtil.UTF_8);

    sender = new ServerID(port, host);

    byte bra = readBuff.getByte(pos);
    requiresAck = bra == (byte)1;
    pos += 1;

    int buffLength = readBuff.getInt(pos);
    pos += 4;
    byte[] payload = readBuff.getBytes(pos, pos + buffLength);
    buff = Buffer.create(payload);
  }

  public void acknowledge() {
    if (bus != null && requiresAck) {
      bus.acknowledge(sender, messageID);
    }
  }

  void write(NetSocket socket) {
    int length = 1 + 6 * 4 + subName.length() + 1 + buff.length() + messageID.length() + sender.host.length();
    Buffer totBuff = Buffer.create(length);
    totBuff.appendInt(0);
    totBuff.appendByte(Sendable.TYPE_MESSAGE);
    writeString(totBuff, messageID);
    writeString(totBuff, subName);
    totBuff.appendInt(sender.port);
    writeString(totBuff, sender.host);
    totBuff.appendByte((byte)(requiresAck ? 1 : 0));
    totBuff.appendInt(buff.length());
    totBuff.appendBuffer(buff);
    totBuff.setInt(0, totBuff.length() - 4);
    socket.write(totBuff);
  }

  byte type() {
    return Sendable.TYPE_MESSAGE;
  }


  Message copy() {
    Message msg = new Message(subName, buff.copy());
    msg.messageID = this.messageID;
    msg.sender = this.sender;
    msg.requiresAck = this.requiresAck;
    return msg;
  }
}
