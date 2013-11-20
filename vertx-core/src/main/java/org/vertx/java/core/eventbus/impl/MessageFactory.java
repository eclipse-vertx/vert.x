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
  static final byte TYPE_JSON_OBJECT = 12;
  static final byte TYPE_JSON_ARRAY = 13;
  static final byte TYPE_REPLY_FAILURE = 100;

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
      case TYPE_JSON_OBJECT:
        return new JsonObjectMessage(buff);
      case TYPE_JSON_ARRAY:
        return new JsonArrayMessage(buff);
      case TYPE_REPLY_FAILURE:
        return new ReplyFailureMessage(buff);
      default:
        throw new IllegalStateException("Invalid type " + type);
    }
  }
}
