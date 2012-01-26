package org.vertx.java.core.eventbus;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.ServerID;

/**
 * <p>Represents a message sent on the event bus.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Message extends Sendable {

  private static final Logger log = Logger.getLogger(Message.class);

  ServerID sender;
  String replyAddress;
  EventBus bus;

  /**
   * The address where the message is being sent
   */
  public String address;

  /**
   * The body (payload) of the message
   */
  public final Buffer body;

  /**
   * Create a new empty Message without specifying messageID - this will be filled in by the system
   * @param address The address to send the message to
   */
  public Message(String address) {
    this(address, null);
  }

  /**
   * Create a new Message without specifying messageID - this will be filled in by the system
   * @param address The address to send the message to
   * @param body
   */
  public Message(String address, Buffer body) {
    if (address == null) {
      throw new IllegalArgumentException("address must be specified");
    }
    this.address = address;
    if (body == null) {
      body = Buffer.create(0);
    }
    this.body = body;
  }

  /**
   * Reply to this message. If the message was sent specifying a receipt handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   * Replying to a message this way is equivalent to sending a message to an address which is the same as the message id
   * of the original message.
   */
  public void reply(Buffer body) {
    if (bus != null && replyAddress != null) {
      if (body == null) {
        body = Buffer.create(0);
      }
      bus.sendBinary(new Message(replyAddress, body));
    }
  }

  /**
   * Same as {@link #reply(Buffer)} but with an empty buffer
   */
  public void reply() {
    reply(null);
  }

  Message(Buffer readBuff) {
    int pos = 1;

    int addressLength = readBuff.getInt(pos);
    pos += 4;
    byte[] addressBytes = readBuff.getBytes(pos, pos + addressLength);
    pos += addressLength;
    address = new String(addressBytes, CharsetUtil.UTF_8);

    int port = readBuff.getInt(pos);
    pos += 4;
    int hostLength = readBuff.getInt(pos);
    pos += 4;
    byte[] hostBytes = readBuff.getBytes(pos, pos + hostLength);
    pos += hostLength;
    String host = new String(hostBytes, CharsetUtil.UTF_8);

    sender = new ServerID(port, host);

    int replyAddressLength = readBuff.getInt(pos);
    pos += 4;
    if (replyAddressLength > 0) {
      byte[] replyAddressBytes = readBuff.getBytes(pos, pos + replyAddressLength);
      pos += replyAddressLength;
      replyAddress = new String(replyAddressBytes, CharsetUtil.UTF_8);
    } else {
      replyAddress = null;
    }

    int buffLength = readBuff.getInt(pos);
    pos += 4;
    byte[] payload = readBuff.getBytes(pos, pos + buffLength);
    body = Buffer.create(payload);
  }

  void write(NetSocket socket) {
    int length = 1 + 4 + address.length() + 1 + 4 * sender.host.length() +
        4 + (replyAddress == null ? 0 : replyAddress.length()) +
        4 + body.length();
    Buffer totBuff = Buffer.create(length);
    totBuff.appendInt(0);
    totBuff.appendByte(Sendable.TYPE_MESSAGE);
    writeString(totBuff, address);
    totBuff.appendInt(sender.port);
    writeString(totBuff, sender.host);
    if (replyAddress != null) {
      writeString(totBuff, replyAddress);
    } else {
      totBuff.appendInt(0);
    }
    totBuff.appendInt(body.length());
    totBuff.appendBuffer(body);
    totBuff.setInt(0, totBuff.length() - 4);
    socket.write(totBuff);
  }

  byte type() {
    return Sendable.TYPE_MESSAGE;
  }

  Message copy() {
    Message msg = new Message(address, body.copy());
    msg.sender = this.sender;
    msg.replyAddress = this.replyAddress;
    msg.bus = this.bus;
    return msg;
  }
}
