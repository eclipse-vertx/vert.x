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

package io.vertx.it;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

import java.util.stream.Collectors;

/**
 * Vanilla implementation for IT tests.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CustomJsonFactory implements JsonFactory {

  public static final JsonCodec CODEC = new JsonCodec() {
    @Override
    public <T> T fromString(String json, Class<T> clazz) throws DecodeException {
      throw new UnsupportedOperationException();
    }
    @Override
    public <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException {
      throw new UnsupportedOperationException();
    }
    @Override
    public <T> T fromValue(Object json, Class<T> toValueType) {
      throw new UnsupportedOperationException();
    }
    @Override
    public String toString(Object object, boolean pretty) throws EncodeException {
      if (object instanceof JsonObject) {
        return ((JsonObject) object).stream()
          .map(member -> "\"" + member.getKey() + "\":" + toString(member.getValue(), pretty))
          .collect(Collectors.joining(",", "{", "}"));
      } else if (object instanceof JsonArray) {
        return ((JsonArray) object).stream().map(elt -> toString(elt, pretty)).collect(Collectors.joining(",", "[", "]"));
      } else if (object instanceof String) {
        return "\"" + object + "\"";
      } else {
        return object.toString();
      }
    }
    @Override
    public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
      return Buffer.buffer(toString(object));
    }
  };

  @Override
  public JsonCodec codec() {
    return CODEC;
  }
}
