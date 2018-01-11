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
