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
package io.vertx.core.json.jackson.v3;

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import static io.vertx.core.json.impl.JsonUtil.BASE64_DECODER;

class ByteArrayDeserializer extends StdDeserializer<byte[]> {

  ByteArrayDeserializer() {
    super(byte[].class);
  }

  @Override
  public byte[] deserialize(JsonParser p, DeserializationContext ctxt) {
    String text = p.getString();
    try {
      return BASE64_DECODER.decode(text);
    } catch (IllegalArgumentException e) {
      throw new InputCoercionException(p, "Expected a base64 encoded byte array", p.currentToken(), byte[].class);
    }
  }
}
