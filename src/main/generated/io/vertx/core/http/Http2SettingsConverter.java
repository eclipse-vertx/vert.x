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
 * Converter for {@link io.vertx.core.http.Http2Settings}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.Http2Settings} original class using Vert.x codegen.
 */
public class Http2SettingsConverter {

  public static void fromJson(JsonObject json, Http2Settings obj) {
    if (json.getValue("headerTableSize") instanceof Number) {
      obj.setHeaderTableSize(((Number)json.getValue("headerTableSize")).longValue());
    }
    if (json.getValue("initialWindowSize") instanceof Number) {
      obj.setInitialWindowSize(((Number)json.getValue("initialWindowSize")).intValue());
    }
    if (json.getValue("maxConcurrentStreams") instanceof Number) {
      obj.setMaxConcurrentStreams(((Number)json.getValue("maxConcurrentStreams")).longValue());
    }
    if (json.getValue("maxFrameSize") instanceof Number) {
      obj.setMaxFrameSize(((Number)json.getValue("maxFrameSize")).intValue());
    }
    if (json.getValue("maxHeaderListSize") instanceof Number) {
      obj.setMaxHeaderListSize(((Number)json.getValue("maxHeaderListSize")).intValue());
    }
    if (json.getValue("pushEnabled") instanceof Boolean) {
      obj.setPushEnabled((Boolean)json.getValue("pushEnabled"));
    }
  }

  public static void toJson(Http2Settings obj, JsonObject json) {
    json.put("headerTableSize", obj.getHeaderTableSize());
    json.put("initialWindowSize", obj.getInitialWindowSize());
    json.put("maxConcurrentStreams", obj.getMaxConcurrentStreams());
    json.put("maxFrameSize", obj.getMaxFrameSize());
    json.put("maxHeaderListSize", obj.getMaxHeaderListSize());
    json.put("pushEnabled", obj.isPushEnabled());
  }
}