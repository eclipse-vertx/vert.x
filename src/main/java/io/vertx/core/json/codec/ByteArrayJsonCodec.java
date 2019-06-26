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

import io.vertx.core.spi.json.JsonCodec;

import java.util.Base64;

public class ByteArrayJsonCodec implements JsonCodec<byte[], String> {

  public static final ByteArrayJsonCodec INSTANCE = new ByteArrayJsonCodec();

  @Override
  public byte[] decode(String json) throws IllegalArgumentException {
    return Base64.getDecoder().decode(json);
  }

  @Override
  public String encode(byte[] value) throws IllegalArgumentException {
    return Base64.getEncoder().encodeToString(value);
  }

  @Override
  public Class<byte[]> getTargetClass() {
    return byte[].class;
  }
}
