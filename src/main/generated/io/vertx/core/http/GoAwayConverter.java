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

package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.http.GoAway}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.GoAway} original class using Vert.x codegen.
 */
public class GoAwayConverter {

  public static void fromJson(JsonObject json, GoAway obj) {
    if (json.getValue("debugData") instanceof String) {
      obj.setDebugData(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("debugData"))));
    }
    if (json.getValue("errorCode") instanceof Number) {
      obj.setErrorCode(((Number)json.getValue("errorCode")).longValue());
    }
    if (json.getValue("lastStreamId") instanceof Number) {
      obj.setLastStreamId(((Number)json.getValue("lastStreamId")).intValue());
    }
  }

  public static void toJson(GoAway obj, JsonObject json) {
    if (obj.getDebugData() != null) {
      json.put("debugData", obj.getDebugData().getBytes());
    }
    json.put("errorCode", obj.getErrorCode());
    json.put("lastStreamId", obj.getLastStreamId());
  }
}
