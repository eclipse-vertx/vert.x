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
 * Converter for {@link io.vertx.core.dns.AddressResolverOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.dns.AddressResolverOptions} original class using Vert.x codegen.
 */
public class AddressResolverOptionsConverter {

  public static void fromJson(JsonObject json, AddressResolverOptions obj) {
    if (json.getValue("cacheMaxTimeToLive") instanceof Number) {
      obj.setCacheMaxTimeToLive(((Number)json.getValue("cacheMaxTimeToLive")).intValue());
    }
    if (json.getValue("cacheMinTimeToLive") instanceof Number) {
      obj.setCacheMinTimeToLive(((Number)json.getValue("cacheMinTimeToLive")).intValue());
    }
    if (json.getValue("cacheNegativeTimeToLive") instanceof Number) {
      obj.setCacheNegativeTimeToLive(((Number)json.getValue("cacheNegativeTimeToLive")).intValue());
    }
    if (json.getValue("hostsPath") instanceof String) {
      obj.setHostsPath((String)json.getValue("hostsPath"));
    }
    if (json.getValue("hostsValue") instanceof String) {
      obj.setHostsValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("hostsValue"))));
    }
    if (json.getValue("maxQueries") instanceof Number) {
      obj.setMaxQueries(((Number)json.getValue("maxQueries")).intValue());
    }
    if (json.getValue("ndots") instanceof Number) {
      obj.setNdots(((Number)json.getValue("ndots")).intValue());
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
    if (json.getValue("searchDomains") instanceof JsonArray) {
      json.getJsonArray("searchDomains").forEach(item -> {
        if (item instanceof String)
          obj.addSearchDomain((String)item);
      });
    }
    if (json.getValue("servers") instanceof JsonArray) {
      json.getJsonArray("servers").forEach(item -> {
        if (item instanceof String)
          obj.addServer((String)item);
      });
    }
  }

  public static void toJson(AddressResolverOptions obj, JsonObject json) {
    json.put("cacheMaxTimeToLive", obj.getCacheMaxTimeToLive());
    json.put("cacheMinTimeToLive", obj.getCacheMinTimeToLive());
    json.put("cacheNegativeTimeToLive", obj.getCacheNegativeTimeToLive());
    if (obj.getHostsPath() != null) {
      json.put("hostsPath", obj.getHostsPath());
    }
    if (obj.getHostsValue() != null) {
      json.put("hostsValue", obj.getHostsValue().getBytes());
    }
    json.put("maxQueries", obj.getMaxQueries());
    json.put("ndots", obj.getNdots());
    json.put("optResourceEnabled", obj.isOptResourceEnabled());
    json.put("queryTimeout", obj.getQueryTimeout());
    json.put("rdFlag", obj.getRdFlag());
    if (obj.getSearchDomains() != null) {
      json.put("searchDomains", new JsonArray(
          obj.getSearchDomains().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    if (obj.getServers() != null) {
      json.put("servers", new JsonArray(
          obj.getServers().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
  }
}