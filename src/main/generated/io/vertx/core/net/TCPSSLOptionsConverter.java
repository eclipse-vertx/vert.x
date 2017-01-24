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
 * Converter for {@link io.vertx.core.net.TCPSSLOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.TCPSSLOptions} original class using Vert.x codegen.
 */
public class TCPSSLOptionsConverter {

  public static void fromJson(JsonObject json, TCPSSLOptions obj) {
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
    if (json.getValue("enabledSecureTransportProtocols") instanceof JsonArray) {
      json.getJsonArray("enabledSecureTransportProtocols").forEach(item -> {
        if (item instanceof String)
          obj.addEnabledSecureTransportProtocol((String)item);
      });
    }
    if (json.getValue("idleTimeout") instanceof Number) {
      obj.setIdleTimeout(((Number)json.getValue("idleTimeout")).intValue());
    }
    if (json.getValue("jdkSslEngineOptions") instanceof JsonObject) {
      obj.setJdkSslEngineOptions(new io.vertx.core.net.JdkSSLEngineOptions((JsonObject)json.getValue("jdkSslEngineOptions")));
    }
    if (json.getValue("keyStoreOptions") instanceof JsonObject) {
      obj.setKeyStoreOptions(new io.vertx.core.net.JksOptions((JsonObject)json.getValue("keyStoreOptions")));
    }
    if (json.getValue("openSslEngineOptions") instanceof JsonObject) {
      obj.setOpenSslEngineOptions(new io.vertx.core.net.OpenSSLEngineOptions((JsonObject)json.getValue("openSslEngineOptions")));
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
    if (json.getValue("soLinger") instanceof Number) {
      obj.setSoLinger(((Number)json.getValue("soLinger")).intValue());
    }
    if (json.getValue("ssl") instanceof Boolean) {
      obj.setSsl((Boolean)json.getValue("ssl"));
    }
    if (json.getValue("tcpKeepAlive") instanceof Boolean) {
      obj.setTcpKeepAlive((Boolean)json.getValue("tcpKeepAlive"));
    }
    if (json.getValue("tcpNoDelay") instanceof Boolean) {
      obj.setTcpNoDelay((Boolean)json.getValue("tcpNoDelay"));
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

  public static void toJson(TCPSSLOptions obj, JsonObject json) {
    if (obj.getCrlPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getCrlPaths().forEach(item -> array.add(item));
      json.put("crlPaths", array);
    }
    if (obj.getCrlValues() != null) {
      JsonArray array = new JsonArray();
      obj.getCrlValues().forEach(item -> array.add(item.getBytes()));
      json.put("crlValues", array);
    }
    if (obj.getEnabledCipherSuites() != null) {
      JsonArray array = new JsonArray();
      obj.getEnabledCipherSuites().forEach(item -> array.add(item));
      json.put("enabledCipherSuites", array);
    }
    if (obj.getEnabledSecureTransportProtocols() != null) {
      JsonArray array = new JsonArray();
      obj.getEnabledSecureTransportProtocols().forEach(item -> array.add(item));
      json.put("enabledSecureTransportProtocols", array);
    }
    json.put("idleTimeout", obj.getIdleTimeout());
    json.put("soLinger", obj.getSoLinger());
    json.put("ssl", obj.isSsl());
    json.put("tcpKeepAlive", obj.isTcpKeepAlive());
    json.put("tcpNoDelay", obj.isTcpNoDelay());
    json.put("useAlpn", obj.isUseAlpn());
    json.put("usePooledBuffers", obj.isUsePooledBuffers());
  }
}