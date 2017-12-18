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
