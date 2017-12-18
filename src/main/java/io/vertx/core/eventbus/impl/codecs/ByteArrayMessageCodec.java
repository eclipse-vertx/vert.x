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
public class ByteArrayMessageCodec implements MessageCodec<byte[], byte[]> {

  @Override
  public void encodeToWire(Buffer buffer, byte[] byteArray) {
    buffer.appendInt(byteArray.length);
    buffer.appendBytes(byteArray);
  }

  @Override
  public byte[] decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    return buffer.getBytes(pos, pos + length);
  }

  @Override
  public byte[] transform(byte[] bytes) {
    byte[] copied = new byte[bytes.length];
    System.arraycopy(bytes, 0, copied, 0, bytes.length);
    return copied;
  }

  @Override
  public String name() {
    return "bytearray";
  }

  @Override
  public byte systemCodecID() {
    return 12;
  }
}
