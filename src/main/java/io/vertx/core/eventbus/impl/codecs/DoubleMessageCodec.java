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
public class DoubleMessageCodec implements MessageCodec<Double, Double> {

  @Override
  public void encodeToWire(Buffer buffer, Double d) {
    buffer.appendDouble(d);
  }

  @Override
  public Double decodeFromWire(int pos, Buffer buffer) {
    return buffer.getDouble(pos);
  }

  @Override
  public Double transform(Double d) {
    // Doubles are immutable so just return it
    return d;
  }

  @Override
  public String name() {
    return "double";
  }

  @Override
  public byte systemCodecID() {
    return 8;
  }
}
