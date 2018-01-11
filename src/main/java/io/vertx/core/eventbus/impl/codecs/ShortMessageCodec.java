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
