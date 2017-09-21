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