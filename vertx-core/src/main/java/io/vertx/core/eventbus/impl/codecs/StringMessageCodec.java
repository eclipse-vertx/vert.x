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

import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StringMessageCodec implements MessageCodec<String, String> {

  @Override
  public void encodeToWire(Buffer buffer, String s) {
    byte[] strBytes = s.getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(strBytes.length);
    buffer.appendBytes(strBytes);
  }

  @Override
  public String decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] bytes = buffer.getBytes(pos, pos + length);
    return new String(bytes, CharsetUtil.UTF_8);
  }

  @Override
  public String transform(String s) {
    // Strings are immutable so just return it
    return s;
  }

  @Override
  public String name() {
    return "string";
  }

  @Override
  public byte systemCodecID() {
    return 9;
  }
}
