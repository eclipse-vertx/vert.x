/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;

/**
 * A Jackson {@code Module} to provide following VertX Serializers and Deserializers
 * that can be reused for building custom mappers :
 * <ul>
 *   <li>{@code JsonObjectSerializer} of {@code JsonObject} </li>
 *   <li>{@code JsonArraySerializer} of {@code JsonArray}</li>
 *   <li>{@code JsonArrayDeserializer} of {@code JsonArray} </li>
 *   <li>{@code JsonObjectDeserializer} of {@code JsonObject}</li>
 *   <li>{@code InstantSerializer} and {@code InstantDeserializer} of {@code Instant}</li>
 *   <li>{@code ByteArraySerializer} and {@code ByteArraySerializer} of {@code byte[]}</li>
 *   <li>{@code BufferSerializer} and {@code BufferSerializer} of {@code Buffer}</li>
 * </ul>
 */
public class VertxModule extends SimpleModule {

  public VertxModule() {
    // custom types
    addSerializer(JsonObject.class, new JsonObjectSerializer());
    addSerializer(JsonArray.class, new JsonArraySerializer());
    addDeserializer(JsonArray.class, new JsonArrayDeserializer());
    addDeserializer(JsonObject.class, new JsonObjectDeserializer());
    // he have 2 extensions: RFC-7493
    addSerializer(Instant.class, new InstantSerializer());
    addDeserializer(Instant.class, new InstantDeserializer());
    addSerializer(byte[].class, new ByteArraySerializer());
    addDeserializer(byte[].class, new ByteArrayDeserializer());
    addSerializer(Buffer.class, new BufferSerializer());
    addDeserializer(Buffer.class, new BufferDeserializer());
  }

}
