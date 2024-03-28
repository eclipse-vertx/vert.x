/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl.clustered;

import io.netty.util.CharsetUtil;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.eventbus.impl.EventBusImpl;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredMessage<U, V> extends MessageImpl<U, V> {

  private static final Logger log = LoggerFactory.getLogger(ClusteredMessage.class);

  private static final byte WIRE_PROTOCOL_VERSION = 2;

  private String sender;
  private String repliedTo;
  private Buffer wireBuffer;
  private int bodyPos;
  private int headersPos;
  private boolean fromWire;
  private boolean toWire;
  private String failure;

  public ClusteredMessage(EventBusImpl bus) {
    super(bus);
  }

  public ClusteredMessage(String sender, String address, MultiMap headers, U sentBody,
                          MessageCodec<U, V> messageCodec, boolean send, EventBusImpl bus) {
    super(address, headers, sentBody, messageCodec, send, bus);
    this.sender = sender;
  }

  protected ClusteredMessage(ClusteredMessage<U, V> other) {
    super(other);
    this.sender = other.sender;
    if (other.sentBody == null) {
      this.wireBuffer = other.wireBuffer;
      this.bodyPos = other.bodyPos;
      this.headersPos = other.headersPos;
    }
    this.fromWire = other.fromWire;
  }

  @Override
  public MessageImpl createReply(Object message, DeliveryOptions options) {
    ClusteredMessage reply = (ClusteredMessage) super.createReply(message, options);
    reply.repliedTo = sender;
    return reply;
  }

  public ClusteredMessage<U, V> copyBeforeReceive() {
    return new ClusteredMessage<>(this);
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
        headers = MultiMap.caseInsensitiveMultiMap();
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
    toWire = true;
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
    buffer.appendByte(send ? (byte) 0 : (byte) 1);
    writeString(buffer, address);
    if (replyAddress != null) {
      writeString(buffer, replyAddress);
    } else {
      buffer.appendInt(0);
    }
    writeString(buffer, sender);
    encodeHeaders(buffer);
    writeBody(buffer);
    buffer.setInt(0, buffer.length() - 4);
    return buffer;
  }

  public void readFromWire(Buffer buffer, CodecManager codecManager) {
    int pos = 0;
    // Overall Length already read when passed in here
    byte protocolVersion = buffer.getByte(pos);
    if (protocolVersion > WIRE_PROTOCOL_VERSION) {
      setFailure("Invalid wire protocol version " + protocolVersion + " should be <= " + WIRE_PROTOCOL_VERSION);
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
      messageCodec = codecManager.getCodec(codecName);
      if (messageCodec == null) {
        setFailure("No message codec registered with name " + codecName);
      }
      pos += length;
    } else {
      messageCodec = codecManager.systemCodecs()[systemCodecCode];
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
    length = buffer.getInt(pos);
    pos += 4;
    bytes = buffer.getBytes(pos, pos + length);
    sender = new String(bytes, CharsetUtil.UTF_8);
    pos += length;
    headersPos = pos;
    int headersLength = buffer.getInt(pos);
    pos += headersLength;
    bodyPos = pos;
    wireBuffer = buffer;
    fromWire = true;
  }

  private void setFailure(String s) {
    if (failure == null) {
      failure = s;
    }
  }

  private void decodeBody() {
    receivedBody = messageCodec.decodeFromWire(bodyPos, wireBuffer);
    bodyPos = 0;
  }

  private void encodeHeaders(Buffer buffer) {
    if (headers != null && !headers.isEmpty()) {
      int headersLengthPos = buffer.length();
      buffer.appendInt(0);
      buffer.appendInt(headers.entries().size());
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
      headers = MultiMap.caseInsensitiveMultiMap();
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

  String getSender() {
    return sender;
  }

  String getRepliedTo() {
    return repliedTo;
  }

  public boolean isFromWire() {
    return fromWire;
  }

  public boolean isToWire() {
    return toWire;
  }

  protected boolean isLocal() {
    return !isFromWire();
  }

  boolean hasFailure() {
    return failure != null;
  }

  void internalError() {
    if (replyAddress != null) {
      reply(new ReplyException(ReplyFailure.ERROR, failure));
    } else {
      log.error(failure);
    }
  }
}
