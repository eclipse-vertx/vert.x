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
 * Converter for {@link io.vertx.core.http.HttpClientOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpClientOptions} original class using Vert.x codegen.
 */
public class HttpClientOptionsConverter {

  public static void fromJson(JsonObject json, HttpClientOptions obj) {
    if (json.getValue("alpnVersions") instanceof JsonArray) {
      java.util.ArrayList<io.vertx.core.http.HttpVersion> list = new java.util.ArrayList<>();
      json.getJsonArray("alpnVersions").forEach( item -> {
        if (item instanceof String)
          list.add(io.vertx.core.http.HttpVersion.valueOf((String)item));
      });
      obj.setAlpnVersions(list);
    }
    if (json.getValue("defaultHost") instanceof String) {
      obj.setDefaultHost((String)json.getValue("defaultHost"));
    }
    if (json.getValue("defaultPort") instanceof Number) {
      obj.setDefaultPort(((Number)json.getValue("defaultPort")).intValue());
    }
    if (json.getValue("http2ClearTextUpgrade") instanceof Boolean) {
      obj.setHttp2ClearTextUpgrade((Boolean)json.getValue("http2ClearTextUpgrade"));
    }
    if (json.getValue("http2ConnectionWindowSize") instanceof Number) {
      obj.setHttp2ConnectionWindowSize(((Number)json.getValue("http2ConnectionWindowSize")).intValue());
    }
    if (json.getValue("http2MaxPoolSize") instanceof Number) {
      obj.setHttp2MaxPoolSize(((Number)json.getValue("http2MaxPoolSize")).intValue());
    }
    if (json.getValue("http2MultiplexingLimit") instanceof Number) {
      obj.setHttp2MultiplexingLimit(((Number)json.getValue("http2MultiplexingLimit")).intValue());
    }
    if (json.getValue("initialSettings") instanceof JsonObject) {
      obj.setInitialSettings(new io.vertx.core.http.Http2Settings((JsonObject)json.getValue("initialSettings")));
    }
    if (json.getValue("keepAlive") instanceof Boolean) {
      obj.setKeepAlive((Boolean)json.getValue("keepAlive"));
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
    if (json.getValue("maxPoolSize") instanceof Number) {
      obj.setMaxPoolSize(((Number)json.getValue("maxPoolSize")).intValue());
    }
    if (json.getValue("maxWaitQueueSize") instanceof Number) {
      obj.setMaxWaitQueueSize(((Number)json.getValue("maxWaitQueueSize")).intValue());
    }
    if (json.getValue("maxWebsocketFrameSize") instanceof Number) {
      obj.setMaxWebsocketFrameSize(((Number)json.getValue("maxWebsocketFrameSize")).intValue());
    }
    if (json.getValue("pipelining") instanceof Boolean) {
      obj.setPipelining((Boolean)json.getValue("pipelining"));
    }
    if (json.getValue("pipeliningLimit") instanceof Number) {
      obj.setPipeliningLimit(((Number)json.getValue("pipeliningLimit")).intValue());
    }
    if (json.getValue("protocolVersion") instanceof String) {
      obj.setProtocolVersion(io.vertx.core.http.HttpVersion.valueOf((String)json.getValue("protocolVersion")));
    }
    if (json.getValue("sendUnmaskedFrames") instanceof Boolean) {
      obj.setSendUnmaskedFrames((Boolean)json.getValue("sendUnmaskedFrames"));
    }
    if (json.getValue("tryUseCompression") instanceof Boolean) {
      obj.setTryUseCompression((Boolean)json.getValue("tryUseCompression"));
    }
    if (json.getValue("verifyHost") instanceof Boolean) {
      obj.setVerifyHost((Boolean)json.getValue("verifyHost"));
    }
  }

  public static void toJson(HttpClientOptions obj, JsonObject json) {
    if (obj.getAlpnVersions() != null) {
      JsonArray array = new JsonArray();
      obj.getAlpnVersions().forEach(item -> array.add(item.name()));
      json.put("alpnVersions", array);
    }
    if (obj.getDefaultHost() != null) {
      json.put("defaultHost", obj.getDefaultHost());
    }
    json.put("defaultPort", obj.getDefaultPort());
    json.put("http2ClearTextUpgrade", obj.isHttp2ClearTextUpgrade());
    json.put("http2ConnectionWindowSize", obj.getHttp2ConnectionWindowSize());
    json.put("http2MaxPoolSize", obj.getHttp2MaxPoolSize());
    json.put("http2MultiplexingLimit", obj.getHttp2MultiplexingLimit());
    if (obj.getInitialSettings() != null) {
      json.put("initialSettings", obj.getInitialSettings().toJson());
    }
    json.put("keepAlive", obj.isKeepAlive());
    json.put("maxChunkSize", obj.getMaxChunkSize());
    json.put("maxHeaderSize", obj.getMaxHeaderSize());
    json.put("maxInitialLineLength", obj.getMaxInitialLineLength());
    json.put("maxPoolSize", obj.getMaxPoolSize());
    json.put("maxWaitQueueSize", obj.getMaxWaitQueueSize());
    json.put("maxWebsocketFrameSize", obj.getMaxWebsocketFrameSize());
    json.put("pipelining", obj.isPipelining());
    json.put("pipeliningLimit", obj.getPipeliningLimit());
    if (obj.getProtocolVersion() != null) {
      json.put("protocolVersion", obj.getProtocolVersion().name());
    }
    json.put("sendUnmaskedFrames", obj.isSendUnmaskedFrames());
    json.put("tryUseCompression", obj.isTryUseCompression());
    json.put("verifyHost", obj.isVerifyHost());
  }
}