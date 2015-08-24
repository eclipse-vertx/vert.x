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
 * Converter for {@link io.vertx.core.http.HttpServerOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpServerOptions} original class using Vert.x codegen.
 */
public class HttpServerOptionsConverter {

  public static void fromJson(JsonObject json, HttpServerOptions obj) {
    if (json.getValue("compressionSupported") instanceof Boolean) {
      obj.setCompressionSupported((Boolean)json.getValue("compressionSupported"));
    }
    if (json.getValue("handle100ContinueAutomatically") instanceof Boolean) {
      obj.setHandle100ContinueAutomatically((Boolean)json.getValue("handle100ContinueAutomatically"));
    }
    if (json.getValue("maxWebsocketFrameSize") instanceof Number) {
      obj.setMaxWebsocketFrameSize(((Number)json.getValue("maxWebsocketFrameSize")).intValue());
    }
    if (json.getValue("websocketSubProtocols") instanceof String) {
      obj.setWebsocketSubProtocols((String)json.getValue("websocketSubProtocols"));
    }
  }

  public static void toJson(HttpServerOptions obj, JsonObject json) {
    json.put("compressionSupported", obj.isCompressionSupported());
    json.put("handle100ContinueAutomatically", obj.isHandle100ContinueAutomatically());
    json.put("maxWebsocketFrameSize", obj.getMaxWebsocketFrameSize());
    if (obj.getWebsocketSubProtocols() != null) {
      json.put("websocketSubProtocols", obj.getWebsocketSubProtocols());
    }
  }
}