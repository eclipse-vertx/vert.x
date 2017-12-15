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
 class PemKeyCertOptionsConverter {

   static void fromJson(JsonObject json, PemKeyCertOptions obj) {
    if (json.getValue("certPath") instanceof String) {
      obj.setCertPath((String)json.getValue("certPath"));
    }
    if (json.getValue("certPaths") instanceof JsonArray) {
      java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
      json.getJsonArray("certPaths").forEach( item -> {
        if (item instanceof String)
          list.add((String)item);
      });
      obj.setCertPaths(list);
    }
    if (json.getValue("certValue") instanceof String) {
      obj.setCertValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("certValue"))));
    }
    if (json.getValue("certValues") instanceof JsonArray) {
      java.util.ArrayList<io.vertx.core.buffer.Buffer> list = new java.util.ArrayList<>();
      json.getJsonArray("certValues").forEach( item -> {
        if (item instanceof String)
          list.add(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)item)));
      });
      obj.setCertValues(list);
    }
    if (json.getValue("keyPath") instanceof String) {
      obj.setKeyPath((String)json.getValue("keyPath"));
    }
    if (json.getValue("keyPaths") instanceof JsonArray) {
      java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
      json.getJsonArray("keyPaths").forEach( item -> {
        if (item instanceof String)
          list.add((String)item);
      });
      obj.setKeyPaths(list);
    }
    if (json.getValue("keyValue") instanceof String) {
      obj.setKeyValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("keyValue"))));
    }
    if (json.getValue("keyValues") instanceof JsonArray) {
      java.util.ArrayList<io.vertx.core.buffer.Buffer> list = new java.util.ArrayList<>();
      json.getJsonArray("keyValues").forEach( item -> {
        if (item instanceof String)
          list.add(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)item)));
      });
      obj.setKeyValues(list);
    }
  }

   static void toJson(PemKeyCertOptions obj, JsonObject json) {
    if (obj.getCertPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getCertPaths().forEach(item -> array.add(item));
      json.put("certPaths", array);
    }
    if (obj.getCertValues() != null) {
      JsonArray array = new JsonArray();
      obj.getCertValues().forEach(item -> array.add(item.getBytes()));
      json.put("certValues", array);
    }
    if (obj.getKeyPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getKeyPaths().forEach(item -> array.add(item));
      json.put("keyPaths", array);
    }
    if (obj.getKeyValues() != null) {
      JsonArray array = new JsonArray();
      obj.getKeyValues().forEach(item -> array.add(item.getBytes()));
      json.put("keyValues", array);
    }
  }
}