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
public class BooleanMessageCodec implements MessageCodec<Boolean, Boolean> {

  @Override
  public void encodeToWire(Buffer buffer, Boolean b) {
    buffer.appendByte((byte)(b ? 0 : 1));
  }

  @Override
  public Boolean decodeFromWire(int pos, Buffer buffer) {
    return buffer.getByte(pos) == 0;
  }

  @Override
  public Boolean transform(Boolean b) {
    // Booleans are immutable so just return it
    return b;
  }

  @Override
  public String name() {
    return "boolean";
  }

  @Override
  public byte systemCodecID() {
    return 3;
  }
}
