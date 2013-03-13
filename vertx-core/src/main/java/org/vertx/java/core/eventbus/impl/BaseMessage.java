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

import io.netty.util.CharsetUtil;
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
public abstract class BaseMessage<U> extends Message<U> {

  private static final Logger log = LoggerFactory.getLogger(BaseMessage.class);

  protected ServerID sender;
  protected DefaultEventBus bus;
  protected String address;
  boolean send; // Is it a send or a publish?

  protected BaseMessage(boolean send, String address, U body) {
    this.send = send;
    this.body = body;
    this.address = address;
    this.body = body;
  }

  protected <T> void doReply(Object message, Handler<Message<T>> replyHandler) {
    sendReply(DefaultEventBus.createMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(JsonObject message, Handler<Message<T>> replyHandler) {
    sendReply(new JsonObjectMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(JsonArray message, Handler<Message<T>> replyHandler) {
    sendReply(new JsonArrayMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(String message, Handler<Message<T>> replyHandler) {
    sendReply(new StringMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Buffer message, Handler<Message<T>> replyHandler) {
    sendReply(new BufferMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(byte[] message, Handler<Message<T>> replyHandler) {
    sendReply(new ByteArrayMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Integer message, Handler<Message<T>> replyHandler) {
    sendReply(new IntMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Long message, Handler<Message<T>> replyHandler) {
    sendReply(new LongMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Short message, Handler<Message<T>> replyHandler) {
    sendReply(new ShortMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Character message, Handler<Message<T>> replyHandler) {
     sendReply(new CharacterMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Boolean message, Handler<Message<T>> replyHandler) {
    sendReply(new BooleanMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Float message, Handler<Message<T>> replyHandler) {
    sendReply(new FloatMessage(true, replyAddress, message), replyHandler);
  }

  protected <T> void doReply(Double message, Handler<Message<T>> replyHandler) {
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

  protected abstract Message<U> copy();

  protected abstract void readBody(int pos, Buffer readBuff);

  protected abstract void writeBody(Buffer buff);

  protected abstract int getBodyLength();

  private <T> void sendReply(BaseMessage msg, Handler<Message<T>> replyHandler) {
    if (bus != null && replyAddress != null) {
      bus.sendReply(sender, msg, replyHandler);
    }
  }
}
