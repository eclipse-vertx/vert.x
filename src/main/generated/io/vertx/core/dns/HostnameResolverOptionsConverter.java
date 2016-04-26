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

package io.vertx.core.dns;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.dns.HostnameResolverOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.dns.HostnameResolverOptions} original class using Vert.x codegen.
 */
public class HostnameResolverOptionsConverter {

  public static void fromJson(JsonObject json, HostnameResolverOptions obj) {
    if (json.getValue("cacheMaxTimeToLive") instanceof Number) {
      obj.setCacheMaxTimeToLive(((Number)json.getValue("cacheMaxTimeToLive")).intValue());
    }
    if (json.getValue("cacheMinTimeToLive") instanceof Number) {
      obj.setCacheMinTimeToLive(((Number)json.getValue("cacheMinTimeToLive")).intValue());
    }
    if (json.getValue("cacheNegativeTimeToLive") instanceof Number) {
      obj.setCacheNegativeTimeToLive(((Number)json.getValue("cacheNegativeTimeToLive")).intValue());
    }
    if (json.getValue("maxQueries") instanceof Number) {
      obj.setMaxQueries(((Number)json.getValue("maxQueries")).intValue());
    }
    if (json.getValue("optResourceEnabled") instanceof Boolean) {
      obj.setOptResourceEnabled((Boolean)json.getValue("optResourceEnabled"));
    }
    if (json.getValue("queryTimeout") instanceof Number) {
      obj.setQueryTimeout(((Number)json.getValue("queryTimeout")).longValue());
    }
    if (json.getValue("rdFlag") instanceof Boolean) {
      obj.setRdFlag((Boolean)json.getValue("rdFlag"));
    }
    if (json.getValue("servers") instanceof JsonArray) {
      json.getJsonArray("servers").forEach(item -> {
        if (item instanceof String)
          obj.addServer((String)item);
      });
    }
  }

  public static void toJson(HostnameResolverOptions obj, JsonObject json) {
    json.put("cacheMaxTimeToLive", obj.getCacheMaxTimeToLive());
    json.put("cacheMinTimeToLive", obj.getCacheMinTimeToLive());
    json.put("cacheNegativeTimeToLive", obj.getCacheNegativeTimeToLive());
    json.put("maxQueries", obj.getMaxQueries());
    json.put("optResourceEnabled", obj.isOptResourceEnabled());
    json.put("queryTimeout", obj.getQueryTimeout());
    json.put("rdFlag", obj.getRdFlag());
    if (obj.getServers() != null) {
      json.put("servers", new JsonArray(
          obj.getServers().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
  }
}