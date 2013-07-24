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
class BufferMessage extends BaseMessage<Buffer> {

  BufferMessage(boolean send, String address, Buffer body) {
    super(send, address, body);
  }

  public BufferMessage(Buffer readBuff) {
    super(readBuff);
  }

  @Override
  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      pos++;
      int buffLength = readBuff.getInt(pos);
      pos += 4;
      byte[] bytes = readBuff.getBytes(pos, pos + buffLength);
      body = new Buffer(bytes);
    }
  }

  @Override
  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendInt(body.length());
      buff.appendBuffer(body);
    }
  }

  @Override
  protected int getBodyLength() {
    return 1 + (body == null ? 0 : 4 + body.length());
  }

  @Override
  protected Message<Buffer> copy() {
    BufferMessage copied = new BufferMessage(send, address, body == null ? null : body.copy());
    copied.replyAddress = this.replyAddress;
    copied.bus = this.bus;
    copied.sender = this.sender;
    return copied;
  }

  @Override
  protected byte type() {
    return MessageFactory.TYPE_BUFFER;
  }

}
