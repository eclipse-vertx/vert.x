/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json.codec;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.json.JsonCodec;

public class BufferJsonCodec implements JsonCodec<Buffer, String> {

  public static final BufferJsonCodec INSTANCE = new BufferJsonCodec();

  @Override
  public Buffer decode(String json) throws IllegalArgumentException {
    return Buffer.buffer(ByteArrayJsonCodec.INSTANCE.decode(json));
  }

  @Override
  public String encode(Buffer value) throws IllegalArgumentException {
    return ByteArrayJsonCodec.INSTANCE.encode(value.getBytes());
  }

  @Override
  public Class<Buffer> getTargetClass() {
    return Buffer.class;
  }
}
