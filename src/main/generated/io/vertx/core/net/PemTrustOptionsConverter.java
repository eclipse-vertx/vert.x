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
 * Converter for {@link io.vertx.core.net.PemTrustOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PemTrustOptions} original class using Vert.x codegen.
 */
public class PemTrustOptionsConverter {

  public static void fromJson(JsonObject json, PemTrustOptions obj) {
    if (json.getValue("certPaths") instanceof JsonArray) {
      json.getJsonArray("certPaths").forEach(item -> {
        if (item instanceof String)
          obj.addCertPath((String)item);
      });
    }
    if (json.getValue("certValues") instanceof JsonArray) {
      json.getJsonArray("certValues").forEach(item -> {
        if (item instanceof String)
          obj.addCertValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)item)));
      });
    }
  }

  public static void toJson(PemTrustOptions obj, JsonObject json) {
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
  }
}