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
public class IntMessageCodec implements MessageCodec<Integer, Integer> {

  @Override
  public void encodeToWire(Buffer buffer, Integer i) {
    buffer.appendInt(i);
  }

  @Override
  public Integer decodeFromWire(int pos, Buffer buffer) {
    return buffer.getInt(pos);
  }

  @Override
  public Integer transform(Integer i) {
    // Integers are immutable so just return it
    return i;
  }

  @Override
  public String name() {
    return "int";
  }

  @Override
  public byte systemCodecID() {
    return 5;
  }
}
