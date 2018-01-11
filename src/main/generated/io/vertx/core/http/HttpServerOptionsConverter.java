/*
 * Copyright (c) 2014 Red Hat, Inc. and others
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
 class HttpServerOptionsConverter {

   static void fromJson(JsonObject json, HttpServerOptions obj) {
    if (json.getValue("acceptUnmaskedFrames") instanceof Boolean) {
      obj.setAcceptUnmaskedFrames((Boolean)json.getValue("acceptUnmaskedFrames"));
    }
    if (json.getValue("alpnVersions") instanceof JsonArray) {
      java.util.ArrayList<io.vertx.core.http.HttpVersion> list = new java.util.ArrayList<>();
      json.getJsonArray("alpnVersions").forEach( item -> {
        if (item instanceof String)
          list.add(io.vertx.core.http.HttpVersion.valueOf((String)item));
      });
      obj.setAlpnVersions(list);
    }
    if (json.getValue("compressionLevel") instanceof Number) {
      obj.setCompressionLevel(((Number)json.getValue("compressionLevel")).intValue());
    }
    if (json.getValue("compressionSupported") instanceof Boolean) {
      obj.setCompressionSupported((Boolean)json.getValue("compressionSupported"));
    }
    if (json.getValue("decoderInitialBufferSize") instanceof Number) {
      obj.setDecoderInitialBufferSize(((Number)json.getValue("decoderInitialBufferSize")).intValue());
    }
    if (json.getValue("decompressionSupported") instanceof Boolean) {
      obj.setDecompressionSupported((Boolean)json.getValue("decompressionSupported"));
    }
    if (json.getValue("handle100ContinueAutomatically") instanceof Boolean) {
      obj.setHandle100ContinueAutomatically((Boolean)json.getValue("handle100ContinueAutomatically"));
    }
    if (json.getValue("http2ConnectionWindowSize") instanceof Number) {
      obj.setHttp2ConnectionWindowSize(((Number)json.getValue("http2ConnectionWindowSize")).intValue());
    }
    if (json.getValue("initialSettings") instanceof JsonObject) {
      obj.setInitialSettings(new io.vertx.core.http.Http2Settings((JsonObject)json.getValue("initialSettings")));
    }
    if (json.getValue("maxChunkSize") instanceof Number) {
      obj.setMaxChunkSize(((Number)json.getValue("maxChunkSize")).intValue());
    }
    if (json.getValue("maxHeaderSize") instanceof Number) {
      obj.setMaxHeaderSize(((Number)json.getValue("maxHeaderSize")).intValue());
    }
    if (json.getValue("maxInitialLineLength") instanceof Number) {
      obj.setMaxInitialLineLength(((Number)json.getValue("maxInitialLineLength")).intValue());
    }
    if (json.getValue("maxWebsocketFrameSize") instanceof Number) {
      obj.setMaxWebsocketFrameSize(((Number)json.getValue("maxWebsocketFrameSize")).intValue());
    }
    if (json.getValue("maxWebsocketMessageSize") instanceof Number) {
      obj.setMaxWebsocketMessageSize(((Number)json.getValue("maxWebsocketMessageSize")).intValue());
    }
    if (json.getValue("websocketSubProtocols") instanceof String) {
      obj.setWebsocketSubProtocols((String)json.getValue("websocketSubProtocols"));
    }
  }

   static void toJson(HttpServerOptions obj, JsonObject json) {
    json.put("acceptUnmaskedFrames", obj.isAcceptUnmaskedFrames());
    if (obj.getAlpnVersions() != null) {
      JsonArray array = new JsonArray();
      obj.getAlpnVersions().forEach(item -> array.add(item.name()));
      json.put("alpnVersions", array);
    }
    json.put("compressionLevel", obj.getCompressionLevel());
    json.put("compressionSupported", obj.isCompressionSupported());
    json.put("decoderInitialBufferSize", obj.getDecoderInitialBufferSize());
    json.put("decompressionSupported", obj.isDecompressionSupported());
    json.put("handle100ContinueAutomatically", obj.isHandle100ContinueAutomatically());
    json.put("http2ConnectionWindowSize", obj.getHttp2ConnectionWindowSize());
    if (obj.getInitialSettings() != null) {
      json.put("initialSettings", obj.getInitialSettings().toJson());
    }
    json.put("maxChunkSize", obj.getMaxChunkSize());
    json.put("maxHeaderSize", obj.getMaxHeaderSize());
    json.put("maxInitialLineLength", obj.getMaxInitialLineLength());
    json.put("maxWebsocketFrameSize", obj.getMaxWebsocketFrameSize());
    json.put("maxWebsocketMessageSize", obj.getMaxWebsocketMessageSize());
    if (obj.getWebsocketSubProtocols() != null) {
      json.put("websocketSubProtocols", obj.getWebsocketSubProtocols());
    }
  }
}