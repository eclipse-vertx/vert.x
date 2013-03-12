/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.eventbus.impl;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.ServerID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseMessage<T> extends Message<T> {

  private static final Logger log = LoggerFactory.getLogger(BaseMessage.class);

  protected ServerID sender;
  protected DefaultEventBus bus;
  protected String address;
  boolean send; // Is it a send or a publish?

  protected BaseMessage(boolean send, String address, T body) {
    this.send = send;
    this.body = body;
    if (address == null) {
      throw new IllegalArgumentException("address must be specified");
    }
    this.address = address;
    this.body = body;
  }

  protected void doReply(Object message, Handler<Message> replyHandler) {
    sendReply(DefaultEventBus.createMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(JsonObject message, Handler<Message> replyHandler) {
    sendReply(new JsonObjectMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(JsonArray message, Handler<Message> replyHandler) {
    sendReply(new JsonArrayMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(String message, Handler<Message> replyHandler) {
    sendReply(new StringMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Buffer message, Handler<Message> replyHandler) {
    sendReply(new BufferMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(byte[] message, Handler<Message> replyHandler) {
    sendReply(new ByteArrayMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Integer message, Handler<Message> replyHandler) {
    sendReply(new IntMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Long message, Handler<Message> replyHandler) {
    sendReply(new LongMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Short message, Handler<Message> replyHandler) {
    sendReply(new ShortMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Character message, Handler<Message> replyHandler) {
    sendReply(new CharacterMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Boolean message, Handler<Message> replyHandler) {
    sendReply(new BooleanMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Float message, Handler<Message> replyHandler) {
    sendReply(new FloatMessage(true, replyAddress, message), replyHandler);
  }

  protected void doReply(Double message, Handler<Message> replyHandler) {
    sendReply(new DoubleMessage(true, replyAddress, message), replyHandler);
  }

  protected BaseMessage(Buffer readBuff) {
    int pos = 1;
    byte bsend = readBuff.getByte(pos);
    send = bsend == 0;
    pos += 1;
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
    readBody(pos, readBuff);
  }

  protected void write(NetSocket socket) {
    int length = 1 + 1 + 4 + address.length() + 1 + 4 * sender.host.length() +
        4 + (replyAddress == null ? 0 : replyAddress.length()) +
        getBodyLength();
    Buffer totBuff = new Buffer(length);
    totBuff.appendInt(0);
    totBuff.appendByte(type());
    totBuff.appendByte(send ? (byte)0 : (byte)1);
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

  protected abstract Message<T> copy();

  protected abstract void readBody(int pos, Buffer readBuff);

  protected abstract void writeBody(Buffer buff);

  protected abstract int getBodyLength();

  private void sendReply(BaseMessage msg, Handler<Message> replyHandler) {
    if (bus != null && replyAddress != null) {
      bus.sendReply(sender, msg, replyHandler);
    }
  }
}
