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

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MessageFactory {

  static final byte TYPE_PING = 0;
  static final byte TYPE_BUFFER = 1;
  static final byte TYPE_BOOLEAN = 2;
  static final byte TYPE_BYTEARRAY = 3;
  static final byte TYPE_BYTE = 4;
  static final byte TYPE_CHARACTER = 5;
  static final byte TYPE_DOUBLE = 6;
  static final byte TYPE_FLOAT = 7;
  static final byte TYPE_INT = 8;
  static final byte TYPE_LONG = 9;
  static final byte TYPE_SHORT = 10;
  static final byte TYPE_STRING = 11;
  static final byte TYPE_JSON = 12;

  static BaseMessage read(Buffer buff) {
    byte type = buff.getByte(0);
    switch (type) {
      case TYPE_PING:
        return new PingMessage(buff);
      case TYPE_BUFFER:
        return new BufferMessage(buff);
      case TYPE_BOOLEAN:
        return new BooleanMessage(buff);
      case TYPE_BYTEARRAY:
        return new ByteArrayMessage(buff);
      case TYPE_BYTE:
        return new ByteMessage(buff);
      case TYPE_CHARACTER:
        return new CharacterMessage(buff);
      case TYPE_DOUBLE:
        return new DoubleMessage(buff);
      case TYPE_FLOAT:
        return new FloatMessage(buff);
      case TYPE_INT:
        return new IntMessage(buff);
      case TYPE_LONG:
        return new LongMessage(buff);
      case TYPE_SHORT:
        return new ShortMessage(buff);
      case TYPE_STRING:
        return new StringMessage(buff);
      case TYPE_JSON:
        return new JsonObjectMessage(buff);
      default:
        throw new IllegalStateException("Invalid type " + type);
    }
  }
}
