/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl;

import io.netty.util.CharsetUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ServerID;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MessageImpl<U, V> implements Message<V> {

  private static final Logger log = LoggerFactory.getLogger(MessageImpl.class);

  private static final byte WIRE_PROTOCOL_VERSION = 1;

  private NetSocket socket;
  private EventBusImpl bus;
  private ServerID sender;
  private String address;
  private String replyAddress;
  private MultiMap headers;
  private U sentBody;
  private V receivedBody;
  private MessageCodec<U, V> messageCodec;
  private boolean send;
  private Buffer wireBuffer;
  private int bodyPos;
  private int headersPos;

  public MessageImpl() {
  }

  public MessageImpl(ServerID sender, String address, String replyAddress, MultiMap headers, U sentBody,
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
    this.socket = other.socket;
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

  NetSocket getSocket() {
    return socket;
  }

  public MessageImpl<U, V> copyBeforeReceive() {
    return new MessageImpl<>(this);
  }

  @Override
  public String address() {
    return address;
  }

  @Override
  public MultiMap headers() {
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

  public void readFromWire(NetSocket socket, Buffer buffer, Map<String, MessageCodec> codecMap, MessageCodec[] systemCodecs) {
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
    this.socket = socket;
  }

  private void decodeBody() {
    receivedBody = messageCodec.decodeFromWire(bodyPos, wireBuffer);
    bodyPos = 0;
  }

  private void encodeHeaders(Buffer buffer) {
    if (headers != null && !headers.isEmpty()) {
      int headersLengthPos = buffer.length();
      buffer.appendInt(0);
      buffer.appendInt(headers.size());
      List<Map.Entry<String, String>> entries = headers.entries();
      for (Map.Entry<String, String> entry: entries) {
        writeString(buffer, entry.getKey());
        writeString(buffer, entry.getValue());
      }
      int headersEndPos = buffer.length();
      buffer.setInt(headersLengthPos, headersEndPos - headersLengthPos);
    } else {
      buffer.appendInt(4);
    }
  }

  private void decodeHeaders() {
    int length = wireBuffer.getInt(headersPos);
    if (length != 4) {
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
    if (replyAddress != null) {
      sendReply(bus.createMessage(true, replyAddress, null,
          new ReplyException(ReplyFailure.RECIPIENT_FAILURE, failureCode, message), null), null, null);
    }
  }

  @Override
  public void reply(Object message) {
    reply(message, new DeliveryOptions(), null);
  }

  @Override
  public <R> void reply(Object message, Handler<AsyncResult<Message<R>>> replyHandler) {
    reply(message, new DeliveryOptions(), replyHandler);
  }

  @Override
  public void reply(Object message, DeliveryOptions options) {
    reply(message, options, null);
  }

  @Override
  public <R> void reply(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler) {
    if (replyAddress != null) {
      sendReply(bus.createMessage(true, replyAddress, options.getHeaders(), message, options.getCodecName()), options, replyHandler);
    }
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
