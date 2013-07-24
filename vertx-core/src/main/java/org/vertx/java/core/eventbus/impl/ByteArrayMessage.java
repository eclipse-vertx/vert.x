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
