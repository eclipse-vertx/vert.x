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
package io.vertx.core.json.jackson.v3;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import io.vertx.core.json.JsonArray;

import java.util.List;

class JsonArrayDeserializer extends StdDeserializer<JsonArray> {

  JsonArrayDeserializer() {
    super(JsonArray.class);
  }

  @Override
  public JsonArray deserialize(JsonParser p, DeserializationContext ctxt) {
    return new JsonArray(p.readValueAs(List.class));
  }
}
