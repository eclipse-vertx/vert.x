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
