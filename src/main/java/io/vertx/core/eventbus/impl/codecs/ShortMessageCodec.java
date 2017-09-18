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
public class ShortMessageCodec implements MessageCodec<Short, Short> {

  @Override
  public void encodeToWire(Buffer buffer, Short s) {
    buffer.appendShort(s);
  }

  @Override
  public Short decodeFromWire(int pos, Buffer buffer) {
    return buffer.getShort(pos);
  }

  @Override
  public Short transform(Short s) {
    // Shorts are immutable so just return it
    return s;
  }

  @Override
  public String name() {
    return "short";
  }

  @Override
  public byte systemCodecID() {
    return 4;
  }
}
