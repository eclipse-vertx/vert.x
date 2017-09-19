/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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

  public static void toJson(PemKeyCertOptions obj, JsonObject json) {
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
