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
public class ByteMessageCodec implements MessageCodec<Byte, Byte> {

  @Override
  public void encodeToWire(Buffer buffer, Byte b) {
    buffer.appendByte(b);
  }

  @Override
  public Byte decodeFromWire(int pos, Buffer buffer) {
    return buffer.getByte(pos);
  }

  @Override
  public Byte transform(Byte b) {
    // Bytes are immutable so just return it
    return b;
  }

  @Override
  public String name() {
    return "byte";
  }

  @Override
  public byte systemCodecID() {
    return 2;
  }
}
