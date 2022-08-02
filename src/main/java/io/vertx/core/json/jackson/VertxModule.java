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
package io.vertx.core.json.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * A Jackson {@code Module} to provide following Vert.x Serializers and Deserializers
 * that can be reused for building custom mappers:
 * <ul>
 *   <li>{@code JsonObjectSerializer} of {@code JsonObject} </li>
 *   <li>{@code JsonArraySerializer} of {@code JsonArray}</li>
 *   <li>{@code InstantSerializer} and {@code InstantDeserializer} of {@code Instant}</li>
 *   <li>{@code LocalTimeSerializer} and {@code LocalTimeDeserializer} of {@code LocalTime}</li>
 *   <li>{@code LocalDateSerializer} and {@code LocalDateDeserializer} of {@code LocalDate}</li>
 *   <li>{@code LocalDateTimeSerializer} and {@code LocalDateTimeDeserializer} of {@code LocalDateTime}</li>
 *   <li>{@code OffsetDateTimeSerializer} and {@code OffsetDateTimeDeserializer} of {@code OffsetDateTime}</li>
 *   <li>{@code ByteArraySerializer} and {@code ByteArrayDeserializer} of {@code byte[]}</li>
 *   <li>{@code BufferSerializer} and {@code BufferDeserializer} of {@code Buffer}</li>
 * </ul>
 */
public class VertxModule extends SimpleModule {

  public VertxModule() {
    // custom types
    addSerializer(JsonObject.class, new JsonObjectSerializer());
    addSerializer(JsonArray.class, new JsonArraySerializer());
    // Extensions to RFC-7493
    addSerializer(Instant.class, new InstantSerializer());
    addDeserializer(Instant.class, new InstantDeserializer());
    addSerializer(LocalTime.class, new LocalTimeSerializer());
    addDeserializer(LocalTime.class, new LocalTimeDeserializer());
    addSerializer(LocalDate.class, new LocalDateSerializer());
    addDeserializer(LocalDate.class, new LocalDateDeserializer());
    addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
    addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
    addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
    addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
    addSerializer(byte[].class, new ByteArraySerializer());
    addDeserializer(byte[].class, new ByteArrayDeserializer());
    addSerializer(Buffer.class, new BufferSerializer());
    addDeserializer(Buffer.class, new BufferDeserializer());
  }

}
