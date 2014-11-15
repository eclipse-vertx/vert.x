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
