/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.eventbus.impl;

import io.netty.util.CharsetUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Headers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.impl.ServerID;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MessageImpl<U, V> implements Message<V> {

  private static final Logger log = LoggerFactory.getLogger(MessageImpl.class);

  private static final byte WIRE_PROTOCOL_VERSION = 1;

  private EventBusImpl bus;
  private ServerID sender;
  private String address;
  private String replyAddress;
  private Headers headers;
  private U sentBody;
  private V receivedBody;
  private MessageCodec<U, V> messageCodec;
  private boolean send;
  private Buffer wireBuffer;
  private int bodyPos;
  private int headersPos;

  public MessageImpl() {
  }

  public MessageImpl(ServerID sender, String address, String replyAddress, Headers headers, U sentBody,
                     MessageCodec<U, V> messageCodec, boolean send) {
    this.sender = sender;
    this.address = address;
    this.replyAddress = replyAddress;
    this.headers = headers;
    this.sentBody = sentBody;
    this.messageCodec = messageCodec;
    this.send = send;
  }

  private MessageImpl(MessageImpl<U, V> other) {
    this.bus = other.bus;
    this.sender = other.sender;
    this.address = other.address;
    this.replyAddress = other.replyAddress;
    this.messageCodec = other.messageCodec;
    if (other.headers != null) {
      List<Map.Entry<String, String>> entries = other.headers.entries();
      this.headers = new CaseInsensitiveHeaders();
      for (Map.Entry<String, String> entry: entries) {
        this.headers.add(entry.getKey(), entry.getValue());
      }
    }
    if (other.sentBody != null) {
      // This will only be true if the message has been sent locally
      this.sentBody = other.sentBody;
      this.receivedBody = messageCodec.transform(other.sentBody);
    } else {
      this.wireBuffer = other.wireBuffer;
      this.bodyPos = other.bodyPos;
      this.headersPos = other.headersPos;
    }
    this.send = other.send;
  }

  public MessageImpl<U, V> copyBeforeReceive() {
    return new MessageImpl<>(this);
  }

  @Override
  public String address() {
    return address;
  }

  @Override
  public Headers headers() {
    // Lazily decode headers
    if (headers == null) {
      // The message has been read from the wire
      if (headersPos != 0) {
        decodeHeaders();
      }
      if (headers == null) {
        headers = new CaseInsensitiveHeaders();
      }
    }
    return headers;
  }

  @Override
  public V body() {
    // Lazily decode the body
    if (receivedBody == null && bodyPos != 0) {
      // The message has been read from the wire
      decodeBody();
    }
    return receivedBody;
  }

  @Override
  public String replyAddress() {
    return replyAddress;
  }

  public Buffer encodeToWire() {
    int length = 1024; // TODO make this configurable
    Buffer buffer = Buffer.buffer(length);
    buffer.appendInt(0);
    buffer.appendByte(WIRE_PROTOCOL_VERSION);
    byte systemCodecID = messageCodec.systemCodecID();
    buffer.appendByte(systemCodecID);
    if (systemCodecID == -1) {
      // User codec
      writeString(buffer, messageCodec.name());
    }
    buffer.appendByte(send ? (byte)0 : (byte)1);
    writeString(buffer, address);
    if (replyAddress != null) {
      writeString(buffer, replyAddress);
    } else {
      buffer.appendInt(0);
    }
    buffer.appendInt(sender.port);
    writeString(buffer, sender.host);
    encodeHeaders(buffer);
    writeBody(buffer);
    buffer.setInt(0, buffer.length() - 4);
//    if (buffer.length()> length) {
//      log.warn("Overshot length " + length + " actual " + buffer.length());
//    }
    return buffer;
  }

  public void readFromWire(Buffer buffer, Map<String, MessageCodec> codecMap, MessageCodec[] systemCodecs) {
    int pos = 0;
    // Overall Length already read when passed in here
    byte protocolVersion = buffer.getByte(pos);
    if (protocolVersion > WIRE_PROTOCOL_VERSION) {
      throw new IllegalStateException("Invalid wire protocol version " + protocolVersion +
                                      " should be <= " + WIRE_PROTOCOL_VERSION);
    }
    pos++;
    byte systemCodecCode = buffer.getByte(pos);
    pos++;
    if (systemCodecCode == -1) {
      // User codec
      int length = buffer.getInt(pos);
      pos += 4;
      byte[] bytes = buffer.getBytes(pos, pos + length);
      String codecName = new String(bytes, CharsetUtil.UTF_8);
      messageCodec = codecMap.get(codecName);
      if (messageCodec == null) {
        throw new IllegalStateException("No message codec registered with name " + codecName);
      }
      pos += length;
    } else {
      messageCodec = systemCodecs[systemCodecCode];
    }
    byte bsend = buffer.getByte(pos);
    send = bsend == 0;
    pos++;
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] bytes = buffer.getBytes(pos, pos + length);
    address = new String(bytes, CharsetUtil.UTF_8);
    pos += length;
    length = buffer.getInt(pos);
    pos += 4;
    if (length != 0) {
      bytes = buffer.getBytes(pos, pos + length);
      replyAddress = new String(bytes, CharsetUtil.UTF_8);
      pos += length;
    }
    int senderPort = buffer.getInt(pos);
    pos += 4;
    length = buffer.getInt(pos);
    pos += 4;
    bytes = buffer.getBytes(pos, pos + length);
    String senderHost = new String(bytes, CharsetUtil.UTF_8);
    pos += length;
    headersPos = pos;
    int headersLength = buffer.getInt(pos);
    pos += headersLength;
    bodyPos = pos;
    sender = new ServerID(senderPort, senderHost);
    wireBuffer = buffer;
  }

  private void decodeBody() {
    receivedBody = messageCodec.decodeFromWire(bodyPos, wireBuffer);
    bodyPos = 0;
  }

  private void encodeHeaders(Buffer buffer) {
    if (headers != null && !headers.isEmpty()) {
      int headersLengthPos = buffer.getByteBuf().writerIndex();
      buffer.appendInt(0);
      buffer.appendInt(headers.size());
      List<Map.Entry<String, String>> entries = headers.entries();
      for (Map.Entry<String, String> entry: entries) {
        writeString(buffer, entry.getKey());
        writeString(buffer, entry.getValue());
      }
      int headersEndPos = buffer.getByteBuf().writerIndex();
      buffer.setInt(headersLengthPos, headersEndPos - headersLengthPos);
    } else {
      buffer.appendInt(4);
    }
  }

  private void decodeHeaders() {
    int length = wireBuffer.getInt(headersPos);
    if (length != 0) {
      headersPos += 4;
      int numHeaders = wireBuffer.getInt(headersPos);
      headersPos += 4;
      headers = new CaseInsensitiveHeaders();
      for (int i = 0; i < numHeaders; i++) {
        int keyLength = wireBuffer.getInt(headersPos);
        headersPos += 4;
        byte[] bytes = wireBuffer.getBytes(headersPos, headersPos + keyLength);
        String key = new String(bytes, CharsetUtil.UTF_8);
        headersPos += keyLength;
        int valLength = wireBuffer.getInt(headersPos);
        headersPos += 4;
        bytes = wireBuffer.getBytes(headersPos, headersPos + valLength);
        String val = new String(bytes, CharsetUtil.UTF_8);
        headersPos += valLength;
        headers.add(key, val);
      }
    }
    headersPos = 0;
  }

  private void writeBody(Buffer buff) {
    messageCodec.encodeToWire(buff, sentBody);
  }

  private void writeString(Buffer buff, String str) {
    byte[] strBytes = str.getBytes(CharsetUtil.UTF_8);
    buff.appendInt(strBytes.length);
    buff.appendBytes(strBytes);
  }

  @Override
  public void fail(int failureCode, String message) {
    sendReply(bus.createMessage(true, replyAddress, null,
              new ReplyException(ReplyFailure.RECIPIENT_FAILURE, failureCode, message), null), null, null);
  }

  @Override
  public void forward(String address) {

  }

  @Override
  public void forward(String address, DeliveryOptions options) {

  }

  @Override
  public void reply(Object message) {
    reply(message, DeliveryOptions.options(), null);
  }

  @Override
  public <R> void reply(Object message, Handler<AsyncResult<Message<R>>> replyHandler) {
    reply(message, DeliveryOptions.options(), replyHandler);
  }

  @Override
  public void reply(Object message, DeliveryOptions options) {
    reply(message, options, null);
  }

  @Override
  public <R> void reply(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler) {
    sendReply(bus.createMessage(true, replyAddress, options.getHeaders(), message, options.getCodecName()), options, replyHandler);
  }

  protected void setReplyAddress(String replyAddress) {
    this.replyAddress = replyAddress;
  }

  protected boolean send() {
    return send;
  }

  protected void setBus(EventBusImpl eventBus) {
    this.bus = eventBus;
  }

  protected MessageCodec codec() {
    return messageCodec;
  }

  private <R> void sendReply(MessageImpl msg, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler) {
    if (bus != null && replyAddress != null) {
      bus.sendReply(sender, msg, options, replyHandler);
    }
  }


}
