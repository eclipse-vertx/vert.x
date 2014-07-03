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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Copyable;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.shareddata.Shareable;

import java.util.Map;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class ObjectMessage<T> extends BaseMessage<T> {

  private String decodedType;
  private MessageCodec<T> codec;
  private boolean encoded;
  private Buffer encodedData;

  public ObjectMessage(boolean send, String address, T object, String decodedType, MessageCodec<T> codec) {
    super(send, address, object);
    this.decodedType = decodedType;
    this.codec = codec;
  }

  public ObjectMessage(Buffer readBuff, Map<String, MessageCodec<T>> codecMap) {
    int pos = readNonBodyFields(readBuff);
    readBody(pos, readBuff, codecMap);
  }

  @Override
  protected byte type() {
    return MessageFactory.TYPE_OBJECT;
  }

  @Override
  protected Message<T> copy() {
    if (body instanceof Shareable) {
      return this;
    } else if (body instanceof Copyable) {
      Copyable copyable = (Copyable)body;
      Object copy = copyable.copy();
      if (copy == body) {
        throw new IllegalStateException("copy() must actually copy the object");
      }
      @SuppressWarnings("unchecked")
      Message<T> ret = new ObjectMessage(send, address, copy, decodedType, codec);
      return ret;
    } else {
      throw new IllegalArgumentException(body.getClass() + " does not implement Copyable or Shareable.");
    }
  }

  @Override
  protected void readBody(int pos, Buffer readBuff) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void readBody(int pos, Buffer readBuff, Map<String, MessageCodec<T>> codecMap) {
    // decodedType
    int decodedTypeLength = readBuff.getInt(pos);
    pos += 4;
    if (decodedTypeLength > 0) {
      byte[] bytes = readBuff.getBytes(pos, pos + decodedTypeLength);
      pos += decodedTypeLength;
      decodedType = new String(bytes, CharsetUtil.UTF_8);
    } else {
      throw new IllegalArgumentException("Could not parse decoded type from buffer");
    }

    codec = codecMap.get(decodedType);
    if (codec == null) {
      throw new RuntimeException("Unable to retrieve message codec for type " + decodedType);
    }

    boolean isNull = readBuff.getByte(pos) == (byte) 0;
    if (!isNull) {
      pos++;
      int buffLength = readBuff.getInt(pos);
      pos += 4;
      byte[] bytes = readBuff.getBytes(pos, pos + buffLength);
      encodedData = Buffer.newBuffer(bytes);
      encoded = true;
    }
    body = codec.decode(encodedData);
  }

  @Override
  protected void writeBody(Buffer buff) {
    writeString(buff, decodedType);

    encode();
    if (encodedData == null) {
      buff.appendByte((byte) 0);
    } else {
      buff.appendByte((byte) 1);
      buff.appendInt(encodedData.length());
      buff.appendBuffer(encodedData);
    }
  }

  @Override
  protected int getBodyLength() {
    encode();
    return 1 + (encodedData == null ? 0 : 4 + encodedData.length());
  }

  private Buffer encode() {
    if (!encoded) {
      encodedData = codec.encode(body);
      encoded = true;
    }

    return encodedData;
  }
}
