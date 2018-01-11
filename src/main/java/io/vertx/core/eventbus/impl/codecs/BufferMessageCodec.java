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
public class BufferMessageCodec implements MessageCodec<Buffer, Buffer> {

  @Override
  public void encodeToWire(Buffer buffer, Buffer b) {
    buffer.appendInt(b.length());
    buffer.appendBuffer(b);
  }

  @Override
  public Buffer decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    return buffer.getBuffer(pos, pos + length);
  }

  @Override
  public Buffer transform(Buffer b) {
    return b.copy();
  }

  @Override
  public String name() {
    return "buffer";
  }

  @Override
  public byte systemCodecID() {
    return 11;
  }
}
