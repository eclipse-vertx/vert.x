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
 * Converter for {@link io.vertx.core.net.OpenSSLEngineOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.OpenSSLEngineOptions} original class using Vert.x codegen.
 */
public class OpenSSLEngineOptionsConverter {

  public static void fromJson(JsonObject json, OpenSSLEngineOptions obj) {
    if (json.getValue("sessionCacheEnabled") instanceof Boolean) {
      obj.setSessionCacheEnabled((Boolean)json.getValue("sessionCacheEnabled"));
    }
  }

  public static void toJson(OpenSSLEngineOptions obj, JsonObject json) {
    json.put("alpnAvailable", obj.isAlpnAvailable());
    json.put("available", obj.isAvailable());
    json.put("sessionCacheEnabled", obj.isSessionCacheEnabled());
  }
}
