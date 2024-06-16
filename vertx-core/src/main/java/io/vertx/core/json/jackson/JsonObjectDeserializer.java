/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.Map;

class JsonObjectDeserializer extends JsonDeserializer<JsonObject> {

  private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {
  };

  public JsonObject deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    return new JsonObject(p.<Map<String, Object>>readValueAs(TYPE_REF));
  }
}
