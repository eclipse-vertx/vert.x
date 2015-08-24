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
 * Converter for {@link io.vertx.core.net.PemKeyCertOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PemKeyCertOptions} original class using Vert.x codegen.
 */
public class PemKeyCertOptionsConverter {

  public static void fromJson(JsonObject json, PemKeyCertOptions obj) {
    if (json.getValue("certPath") instanceof String) {
      obj.setCertPath((String)json.getValue("certPath"));
    }
    if (json.getValue("certValue") instanceof String) {
      obj.setCertValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("certValue"))));
    }
    if (json.getValue("keyPath") instanceof String) {
      obj.setKeyPath((String)json.getValue("keyPath"));
    }
    if (json.getValue("keyValue") instanceof String) {
      obj.setKeyValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("keyValue"))));
    }
  }

  public static void toJson(PemKeyCertOptions obj, JsonObject json) {
    if (obj.getCertPath() != null) {
      json.put("certPath", obj.getCertPath());
    }
    if (obj.getCertValue() != null) {
      json.put("certValue", obj.getCertValue().getBytes());
    }
    if (obj.getKeyPath() != null) {
      json.put("keyPath", obj.getKeyPath());
    }
    if (obj.getKeyValue() != null) {
      json.put("keyValue", obj.getKeyValue().getBytes());
    }
  }
}