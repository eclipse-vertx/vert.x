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
public class LongMessageCodec implements MessageCodec<Long, Long> {

  @Override
  public void encodeToWire(Buffer buffer, Long l) {
    buffer.appendLong(l);
  }

  @Override
  public Long decodeFromWire(int pos, Buffer buffer) {
    return buffer.getLong(pos);
  }

  @Override
  public Long transform(Long l) {
    // Longs are immutable so just return it
    return l;
  }

  @Override
  public String name() {
    return "long";
  }

  @Override
  public byte systemCodecID() {
    return 6;
  }
}
