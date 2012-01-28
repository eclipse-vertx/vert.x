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
public abstract class Message<T>  {

  private static final Logger log = Logger.getLogger(Message.class);

  static final byte TYPE_BUFFER = 1;
  static final byte TYPE_BOOLEAN = 2;
  static final byte TYPE_BYTEARRAY = 3;
  static final byte TYPE_BYTE = 4;
  static final byte TYPE_CHARACTER = 5;
  static final byte TYPE_DOUBLE = 6;
  static final byte TYPE_FLOAT = 7;
  static final byte TYPE_INT = 8;
  static final byte TYPE_LONG = 9;
  static final byte TYPE_SHORT = 10;
  static final byte TYPE_STRING = 11;
  static final byte TYPE_JSON = 12;

  static Message read(Buffer buff) {
    byte type = buff.getByte(0);
    switch (type) {
      case TYPE_BUFFER:
        return new BufferMessage(buff);
      case TYPE_BOOLEAN:
        return new BooleanMessage(buff);
      case TYPE_BYTEARRAY:
        return new ByteArrayMessage(buff);
      case TYPE_BYTE:
        return new ByteMessage(buff);
      case TYPE_CHARACTER:
        return new CharacterMessage(buff);
      case TYPE_DOUBLE:
        return new DoubleMessage(buff);
      case TYPE_FLOAT:
        return new FloatMessage(buff);
      case TYPE_INT:
        return new IntMessage(buff);
      case TYPE_LONG:
        return new LongMessage(buff);
      case TYPE_SHORT:
        return new ShortMessage(buff);
      case TYPE_STRING:
        return new StringMessage(buff);
      case TYPE_JSON:
        return new JsonMessage(buff);
      default:
        throw new IllegalStateException("Invalid type " + type);
    }
  }

  ServerID sender;
  String replyAddress;
  EventBus bus;
  String address;

  /**
   * The body of the message
   */
  public T body;

  /**
   * Create a new Message without specifying messageID - this will be filled in by the system
   * @param address The address to send the message to
   * @param body
   */
  Message(String address, T body) {
    this.address = address;
    this.body = body;
  }

  /**
   * Reply to this message. If the message was sent specifying a receipt handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   * Replying to a message this way is equivalent to sending a message to an address which is the same as the message id
   * of the original message.
   */
  public void reply(T message) {
    if (bus != null && replyAddress != null) {
      handleReply(message);
    }
  }

  /**
   * Same as {@link #reply(T)} but with an empty payload
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
    body = readBody(pos, readBuff);
  }

  protected void write(NetSocket socket) {
    int length = 1 + 4 + address.length() + 1 + 4 * sender.host.length() +
        4 + (replyAddress == null ? 0 : replyAddress.length()) +
        getBodyLength();
    Buffer totBuff = Buffer.create(length);
    totBuff.appendInt(0);
    totBuff.appendByte(type());
    writeString(totBuff, address);
    totBuff.appendInt(sender.port);
    writeString(totBuff, sender.host);
    if (replyAddress != null) {
      writeString(totBuff, replyAddress);
    } else {
      totBuff.appendInt(0);
    }
    writeBody(totBuff);
    totBuff.setInt(0, totBuff.length() - 4);
    socket.write(totBuff);
  }

  protected void writeString(Buffer buff, String str) {
    byte[] strBytes = str.getBytes(CharsetUtil.UTF_8);
    buff.appendInt(strBytes.length);
    buff.appendBytes(strBytes);
  }

  protected abstract byte type();

  protected abstract Message copy();

  protected abstract T readBody(int pos, Buffer readBuff);

  protected abstract void writeBody(Buffer buff);

  protected abstract int getBodyLength();

  protected abstract void handleReply(T reply);
}
