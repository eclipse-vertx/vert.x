/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
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

import java.math.BigInteger;

public class BigIntegerMessageCodec implements MessageCodec<BigInteger, BigInteger> {

  @Override
  public void encodeToWire(Buffer buffer, BigInteger bigInteger) {
    byte[] bytes = bigInteger.toString().getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(bytes.length);
    buffer.appendBytes(bytes);
  }

  @Override
  public BigInteger decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] bytes = buffer.getBytes(pos, pos + length);
    return new BigInteger(new String(bytes, CharsetUtil.UTF_8));
  }

  @Override
  public BigInteger transform(BigInteger bigInteger) {
    // BigIntegers are immutable so just return it
    return bigInteger;
  }

  @Override
  public String name() {
    return "biginteger";
  }

  @Override
  public byte systemCodecID() {
    return 16;
  }
}
