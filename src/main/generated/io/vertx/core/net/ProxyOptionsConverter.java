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

package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.net.ProxyOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.ProxyOptions} original class using Vert.x codegen.
 */
public class ProxyOptionsConverter {

  public static void fromJson(JsonObject json, ProxyOptions obj) {
    if (json.getValue("proxyHost") instanceof String) {
      obj.setProxyHost((String)json.getValue("proxyHost"));
    }
    if (json.getValue("proxyPassword") instanceof String) {
      obj.setProxyPassword((String)json.getValue("proxyPassword"));
    }
    if (json.getValue("proxyPort") instanceof Number) {
      obj.setProxyPort(((Number)json.getValue("proxyPort")).intValue());
    }
    if (json.getValue("proxyType") instanceof String) {
      obj.setProxyType(io.vertx.core.net.ProxyType.valueOf((String)json.getValue("proxyType")));
    }
    if (json.getValue("proxyUsername") instanceof String) {
      obj.setProxyUsername((String)json.getValue("proxyUsername"));
    }
  }

  public static void toJson(ProxyOptions obj, JsonObject json) {
    if (obj.getProxyHost() != null) {
      json.put("proxyHost", obj.getProxyHost());
    }
    if (obj.getProxyPassword() != null) {
      json.put("proxyPassword", obj.getProxyPassword());
    }
    json.put("proxyPort", obj.getProxyPort());
    if (obj.getProxyType() != null) {
      json.put("proxyType", obj.getProxyType().name());
    }
    if (obj.getProxyUsername() != null) {
      json.put("proxyUsername", obj.getProxyUsername());
    }
  }
}