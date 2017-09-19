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
 * Converter for {@link io.vertx.core.http.HttpServerOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpServerOptions} original class using Vert.x codegen.
 */
public class HttpServerOptionsConverter {

  public static void fromJson(JsonObject json, HttpServerOptions obj) {
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

  public static void toJson(HttpServerOptions obj, JsonObject json) {
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
