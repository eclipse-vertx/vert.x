/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl.codecs;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CharMessageCodec implements MessageCodec<Character, Character> {

  @Override
  public void encodeToWire(Buffer buffer, Character chr) {
    buffer.appendShort((short)chr.charValue());
  }

  @Override
  public Character decodeFromWire(int pos, Buffer buffer) {
    return (char)buffer.getShort(pos);
  }

  @Override
  public Character transform(Character c) {
    // Characters are immutable so just return it
    return c;
  }

  @Override
  public String name() {
    return "char";
  }

  @Override
  public byte systemCodecID() {
    return 10;
  }
}
