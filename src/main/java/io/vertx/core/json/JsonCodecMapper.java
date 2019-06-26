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
package io.vertx.core.json;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.codec.BufferJsonCodec;
import io.vertx.core.json.codec.ByteArrayJsonCodec;
import io.vertx.core.json.codec.InstantJsonCodec;
import io.vertx.core.spi.json.JsonCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

class JsonCodecMapper implements JsonMapper {

  private final Map<Class, JsonCodec> codecMap;

  JsonCodecMapper() {
    this.codecMap = new HashMap<>();

    // Load vert.x core codecs
    addJsonCodec(BufferJsonCodec.INSTANCE);
    addJsonCodec(ByteArrayJsonCodec.INSTANCE);
    addJsonCodec(InstantJsonCodec.INSTANCE);

    // Load from service loader
    ServiceLoader<JsonCodec> codecServiceLoader = ServiceLoader.load(JsonCodec.class);
    codecServiceLoader.forEach(this::addJsonCodec);
  }

  private void addJsonCodec(JsonCodec j) {
    this.codecMap.put(j.getTargetClass(), j);
  }

  private <T> JsonCodec codec(Class<T> c) {
    return codecMap.get(c);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T decode(Object json, Class<T> c) {
    if (json == null) {
      return null;
    }
    JsonCodec<T, Object> codec = (JsonCodec<T, Object>) codecMap.get(c);
    if (codec == null) {
      throw new IllegalStateException("Unable to find codec for class " + c.getName());
    }
    try {
      return codec.decode(json);
    } catch (Exception e) {
      throw new DecodeException(e);
    }
  }

  @Override
  public <T> T decode(Object json, TypeReference<T> t) throws DecodeException, IllegalStateException {
    throw new DecodeException("Cannot handle TypeReference. Are you missing jackson-databind dependency?");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object encode(Object value) {
    if (value == null) {
      return null;
    }
    JsonCodec<Object, Object> codec = (JsonCodec<Object, Object>) codecMap.get(value.getClass());
    if (codec == null) {
      throw new IllegalStateException("Unable to find codec for class " + value.getClass().getName());
    }
    try {
      return codec.encode(value);
    } catch (Exception e) {
      throw new EncodeException(e);
    }
  }
}
