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
public class FloatMessageCodec implements MessageCodec<Float, Float> {

  @Override
  public void encodeToWire(Buffer buffer, Float f) {
    buffer.appendFloat(f);
  }

  @Override
  public Float decodeFromWire(int pos, Buffer buffer) {
    return buffer.getFloat(pos);
  }

  @Override
  public Float transform(Float f) {
    // Floats are immutable so just return it
    return f;
  }

  @Override
  public String name() {
    return "float";
  }

  @Override
  public byte systemCodecID() {
    return 7;
  }
}
