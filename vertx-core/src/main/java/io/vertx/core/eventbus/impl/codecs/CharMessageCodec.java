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
