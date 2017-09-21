/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.net.JksOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.JksOptions} original class using Vert.x codegen.
 */
public class JksOptionsConverter {

  public static void fromJson(JsonObject json, JksOptions obj) {
    if (json.getValue("password") instanceof String) {
      obj.setPassword((String)json.getValue("password"));
    }
    if (json.getValue("path") instanceof String) {
      obj.setPath((String)json.getValue("path"));
    }
    if (json.getValue("value") instanceof String) {
      obj.setValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("value"))));
    }
  }

  public static void toJson(JksOptions obj, JsonObject json) {
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getValue() != null) {
      json.put("value", obj.getValue().getBytes());
    }
  }
}