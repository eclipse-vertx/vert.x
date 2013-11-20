/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.eventbus.impl;

import io.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class JsonArrayMessage extends BaseMessage<JsonArray> {

  private byte[] encoded;

  JsonArrayMessage(boolean send, String address, JsonArray body) {
    super(send, address, body);
  }

  private JsonArrayMessage(JsonArrayMessage other) {
    super(other.send, other.address, other.body == null ? null : other.body.copy());
    this.replyAddress = other.replyAddress;
    this.bus = other.bus;
    this.sender = other.sender;
  }

  public JsonArrayMessage(Buffer readBuff) {
    super(readBuff);
  }

  @Override
  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      pos++;
      int strLength = readBuff.getInt(pos);
      pos += 4;
      byte[] bytes = readBuff.getBytes(pos, pos + strLength);
      String str = new String(bytes, CharsetUtil.UTF_8);
      body = new JsonArray(str);
    }
  }

  @Override
  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendInt(encoded.length);
      buff.appendBytes(encoded);
    }
  }

  @Override
  protected int getBodyLength() {
    if (body == null) {
      return 1;
    } else {
      String strJson = body.encode();
      encoded = strJson.getBytes(CharsetUtil.UTF_8);
      return 1 + 4 + encoded.length;
    }
  }

  @Override
  protected Message<JsonArray> copy() {
    return new JsonArrayMessage(this);
  }

  @Override
  protected byte type() {
    return MessageFactory.TYPE_JSON_ARRAY;
  }

}
