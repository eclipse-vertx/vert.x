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

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ByteArrayMessage extends BaseMessage<byte[]> {

  ByteArrayMessage(boolean send, String address, byte[] body) {
    super(send, address, body);
  }

  public ByteArrayMessage(Buffer readBuff) {
    super(readBuff);
  }

  @Override
  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      pos++;
      int buffLength = readBuff.getInt(pos);
      pos += 4;
      body = readBuff.getBytes(pos, pos + buffLength);
    }
  }

  @Override
  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendInt(body.length);
      buff.appendBytes(body);
    }
  }

  @Override
  protected int getBodyLength() {
    return body == null ? 1 : 1 + 4 + body.length;
  }

  @Override
  protected Message<byte[]> copy() {
    byte[] bod;
    if (body != null) {
      bod = new byte[body.length];
      System.arraycopy(body, 0, bod, 0, bod.length);
    } else {
      bod = null;
    }
    ByteArrayMessage copied = new ByteArrayMessage(send, address, bod);
    copied.replyAddress = this.replyAddress;
    copied.bus = this.bus;
    copied.sender = this.sender;
    return copied;
  }

  @Override
  protected byte type() {
    return MessageFactory.TYPE_BYTEARRAY;
  }

}
