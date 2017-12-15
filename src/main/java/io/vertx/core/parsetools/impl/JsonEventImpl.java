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

package io.vertx.core.parsetools.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonEvent;
import io.vertx.core.parsetools.JsonEventType;

import java.time.Instant;
import java.util.Base64;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonEventImpl implements JsonEvent {

  private final JsonEventType type;
  private final String field;
  private final Object value;
  private final TokenBuffer buffer;

  public JsonEventImpl(JsonEventType type, String field, Object value) {
    this(type, field, value, null);
  }

  public JsonEventImpl(JsonEventType type, String field, Object value, TokenBuffer buffer) {
    this.type = type;
    this.field = field;
    this.value = value;
    this.buffer = buffer;
  }

  @Override
  public JsonEventType type() {
    return type;
  }

  @Override
  public String fieldName() {
    return field;
  }

  @Override
  public Object value() {
    return value;
  }

  @Override
  public boolean isNumber() {
    return value instanceof Number;
  }

  @Override
  public boolean isBoolean() {
    return value instanceof Boolean;
  }

  @Override
  public boolean isString() {
    return value instanceof String;
  }

  @Override
  public boolean isNull() {
    return type == JsonEventType.VALUE && value == null;
  }

  @Override
  public boolean isObject() {
    return value instanceof JsonObject;
  }

  @Override
  public boolean isArray() {
    return value instanceof JsonArray;
  }

  @Override
  public <T> T mapTo(Class<T> type) {
    if (buffer != null) {
      try {
        return Json.mapper.readValue(buffer.asParser(), type);
      } catch (Exception e) {
        throw new DecodeException(e.getMessage());
      }
    } else {
      return Json.decodeValue(String.valueOf(value), type);
    }
  }

  @Override
  public <T> T mapTo(TypeReference<T> type) {
    if (buffer != null) {
      try {
        return Json.mapper.readValue(buffer.asParser(), type);
      } catch (Exception e) {
        throw new DecodeException(e.getMessage());
      }
    } else {
      return Json.decodeValue(String.valueOf(value), type);
    }
  }

  @Override
  public Integer integerValue() {
    if (value != null) {
      Number number = (Number) value;
      if (value instanceof Integer) {
        return (Integer)value;  // Avoids unnecessary unbox/box
      } else {
        return number.intValue();
      }
    }
    return null;
  }

  @Override
  public Long longValue() {
    if (value != null) {
      Number number = (Number) value;
      if (value instanceof Integer) {
        return (Long)value;  // Avoids unnecessary unbox/box
      } else {
        return number.longValue();
      }
    }
    return null;
  }

  @Override
  public Float floatValue() {
    if (value != null) {
      Number number = (Number) value;
      if (value instanceof Float) {
        return (Float)value;  // Avoids unnecessary unbox/box
      } else {
        return number.floatValue();
      }
    }
    return null;
  }

  @Override
  public Double doubleValue() {
    if (value != null) {
      Number number = (Number) value;
      if (value instanceof Double) {
        return (Double)value;  // Avoids unnecessary unbox/box
      } else {
        return number.doubleValue();
      }
    }
    return null;
  }

  @Override
  public Boolean booleanValue() {
    return (Boolean) value;
  }

  @Override
  public String stringValue() {
    return (String) value;
  }

  @Override
  public Buffer binaryValue() {
    return value != null ? Buffer.buffer(Base64.getDecoder().decode((String) value)) : null;
  }

  @Override
  public Instant instantValue() {
    return value != null ? Instant.from(ISO_INSTANT.parse((CharSequence) value)) : null;
  }

  @Override
  public JsonObject objectValue() {
    return (JsonObject) value;
  }

  @Override
  public JsonArray arrayValue() {
    return (JsonArray) value;
  }
}
