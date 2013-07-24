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
class ShortMessage extends BaseMessage<Short> {

  ShortMessage(boolean send, String address, Short body) {
    super(send, address, body);
  }

  public ShortMessage(Buffer readBuff) {
    super(readBuff);
  }

  @Override
  protected void readBody(int pos, Buffer readBuff) {
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    if (!isNull) {
      body = readBuff.getShort(++pos);
    }
  }

  @Override
  protected void writeBody(Buffer buff) {
    if (body == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendShort(body);
    }
  }

  @Override
  protected int getBodyLength() {
    return 1 + (body == null ? 0 : 2);
  }

  @Override
  protected Message<Short> copy() {
    // No need to copy since everything is immutable
    return this;
  }

  @Override
  protected byte type() {
    return MessageFactory.TYPE_SHORT;
  }

}
