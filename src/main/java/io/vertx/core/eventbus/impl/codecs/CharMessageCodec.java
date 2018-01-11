/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
