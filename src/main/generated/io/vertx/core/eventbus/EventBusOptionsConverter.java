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

package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.eventbus.EventBusOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.eventbus.EventBusOptions} original class using Vert.x codegen.
 */
public class EventBusOptionsConverter {

  public static void fromJson(JsonObject json, EventBusOptions obj) {
    if (json.getValue("acceptBacklog") instanceof Number) {
      obj.setAcceptBacklog(((Number)json.getValue("acceptBacklog")).intValue());
    }
    if (json.getValue("clientAuth") instanceof String) {
      obj.setClientAuth(io.vertx.core.http.ClientAuth.valueOf((String)json.getValue("clientAuth")));
    }
    if (json.getValue("clusterPingInterval") instanceof Number) {
      obj.setClusterPingInterval(((Number)json.getValue("clusterPingInterval")).longValue());
    }
    if (json.getValue("clusterPingReplyInterval") instanceof Number) {
      obj.setClusterPingReplyInterval(((Number)json.getValue("clusterPingReplyInterval")).longValue());
    }
    if (json.getValue("clusterPublicHost") instanceof String) {
      obj.setClusterPublicHost((String)json.getValue("clusterPublicHost"));
    }
    if (json.getValue("clusterPublicPort") instanceof Number) {
      obj.setClusterPublicPort(((Number)json.getValue("clusterPublicPort")).intValue());
    }
    if (json.getValue("clustered") instanceof Boolean) {
      obj.setClustered((Boolean)json.getValue("clustered"));
    }
    if (json.getValue("connectTimeout") instanceof Number) {
      obj.setConnectTimeout(((Number)json.getValue("connectTimeout")).intValue());
    }
    if (json.getValue("crlPaths") instanceof JsonArray) {
      json.getJsonArray("crlPaths").forEach(item -> {
        if (item instanceof String)
          obj.addCrlPath((String)item);
      });
    }
    if (json.getValue("crlValues") instanceof JsonArray) {
      json.getJsonArray("crlValues").forEach(item -> {
        if (item instanceof String)
          obj.addCrlValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)item)));
      });
    }
    if (json.getValue("enabledCipherSuites") instanceof JsonArray) {
      json.getJsonArray("enabledCipherSuites").forEach(item -> {
        if (item instanceof String)
          obj.addEnabledCipherSuite((String)item);
      });
    }
    if (json.getValue("host") instanceof String) {
      obj.setHost((String)json.getValue("host"));
    }
    if (json.getValue("idleTimeout") instanceof Number) {
      obj.setIdleTimeout(((Number)json.getValue("idleTimeout")).intValue());
    }
    if (json.getValue("keyStoreOptions") instanceof JsonObject) {
      obj.setKeyStoreOptions(new io.vertx.core.net.JksOptions((JsonObject)json.getValue("keyStoreOptions")));
    }
    if (json.getValue("pemKeyCertOptions") instanceof JsonObject) {
      obj.setPemKeyCertOptions(new io.vertx.core.net.PemKeyCertOptions((JsonObject)json.getValue("pemKeyCertOptions")));
    }
    if (json.getValue("pemTrustOptions") instanceof JsonObject) {
      obj.setPemTrustOptions(new io.vertx.core.net.PemTrustOptions((JsonObject)json.getValue("pemTrustOptions")));
    }
    if (json.getValue("pfxKeyCertOptions") instanceof JsonObject) {
      obj.setPfxKeyCertOptions(new io.vertx.core.net.PfxOptions((JsonObject)json.getValue("pfxKeyCertOptions")));
    }
    if (json.getValue("pfxTrustOptions") instanceof JsonObject) {
      obj.setPfxTrustOptions(new io.vertx.core.net.PfxOptions((JsonObject)json.getValue("pfxTrustOptions")));
    }
    if (json.getValue("port") instanceof Number) {
      obj.setPort(((Number)json.getValue("port")).intValue());
    }
    if (json.getValue("receiveBufferSize") instanceof Number) {
      obj.setReceiveBufferSize(((Number)json.getValue("receiveBufferSize")).intValue());
    }
    if (json.getValue("reconnectAttempts") instanceof Number) {
      obj.setReconnectAttempts(((Number)json.getValue("reconnectAttempts")).intValue());
    }
    if (json.getValue("reconnectInterval") instanceof Number) {
      obj.setReconnectInterval(((Number)json.getValue("reconnectInterval")).longValue());
    }
    if (json.getValue("reuseAddress") instanceof Boolean) {
      obj.setReuseAddress((Boolean)json.getValue("reuseAddress"));
    }
    if (json.getValue("sendBufferSize") instanceof Number) {
      obj.setSendBufferSize(((Number)json.getValue("sendBufferSize")).intValue());
    }
    if (json.getValue("soLinger") instanceof Number) {
      obj.setSoLinger(((Number)json.getValue("soLinger")).intValue());
    }
    if (json.getValue("ssl") instanceof Boolean) {
      obj.setSsl((Boolean)json.getValue("ssl"));
    }
    if (json.getValue("sslEngine") instanceof String) {
      obj.setSslEngine(io.vertx.core.net.SSLEngine.valueOf((String)json.getValue("sslEngine")));
    }
    if (json.getValue("tcpKeepAlive") instanceof Boolean) {
      obj.setTcpKeepAlive((Boolean)json.getValue("tcpKeepAlive"));
    }
    if (json.getValue("tcpNoDelay") instanceof Boolean) {
      obj.setTcpNoDelay((Boolean)json.getValue("tcpNoDelay"));
    }
    if (json.getValue("trafficClass") instanceof Number) {
      obj.setTrafficClass(((Number)json.getValue("trafficClass")).intValue());
    }
    if (json.getValue("trustAll") instanceof Boolean) {
      obj.setTrustAll((Boolean)json.getValue("trustAll"));
    }
    if (json.getValue("trustStoreOptions") instanceof JsonObject) {
      obj.setTrustStoreOptions(new io.vertx.core.net.JksOptions((JsonObject)json.getValue("trustStoreOptions")));
    }
    if (json.getValue("useAlpn") instanceof Boolean) {
      obj.setUseAlpn((Boolean)json.getValue("useAlpn"));
    }
    if (json.getValue("usePooledBuffers") instanceof Boolean) {
      obj.setUsePooledBuffers((Boolean)json.getValue("usePooledBuffers"));
    }
  }

  public static void toJson(EventBusOptions obj, JsonObject json) {
    json.put("acceptBacklog", obj.getAcceptBacklog());
    if (obj.getClientAuth() != null) {
      json.put("clientAuth", obj.getClientAuth().name());
    }
    json.put("clusterPingInterval", obj.getClusterPingInterval());
    json.put("clusterPingReplyInterval", obj.getClusterPingReplyInterval());
    if (obj.getClusterPublicHost() != null) {
      json.put("clusterPublicHost", obj.getClusterPublicHost());
    }
    json.put("clusterPublicPort", obj.getClusterPublicPort());
    json.put("clustered", obj.isClustered());
    json.put("connectTimeout", obj.getConnectTimeout());
    if (obj.getCrlPaths() != null) {
      json.put("crlPaths", new JsonArray(
          obj.getCrlPaths().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    if (obj.getCrlValues() != null) {
      json.put("crlValues", new JsonArray(
          obj.getCrlValues().
              stream().
              map(item -> item.getBytes()).
              collect(java.util.stream.Collectors.toList())));
    }
    if (obj.getEnabledCipherSuites() != null) {
      json.put("enabledCipherSuites", new JsonArray(
          obj.getEnabledCipherSuites().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("idleTimeout", obj.getIdleTimeout());
    json.put("port", obj.getPort());
    json.put("receiveBufferSize", obj.getReceiveBufferSize());
    json.put("reconnectAttempts", obj.getReconnectAttempts());
    json.put("reconnectInterval", obj.getReconnectInterval());
    json.put("reuseAddress", obj.isReuseAddress());
    json.put("sendBufferSize", obj.getSendBufferSize());
    json.put("soLinger", obj.getSoLinger());
    json.put("ssl", obj.isSsl());
    if (obj.getSslEngine() != null) {
      json.put("sslEngine", obj.getSslEngine().name());
    }
    json.put("tcpKeepAlive", obj.isTcpKeepAlive());
    json.put("tcpNoDelay", obj.isTcpNoDelay());
    json.put("trafficClass", obj.getTrafficClass());
    json.put("trustAll", obj.isTrustAll());
    json.put("useAlpn", obj.isUseAlpn());
    json.put("usePooledBuffers", obj.isUsePooledBuffers());
  }
}