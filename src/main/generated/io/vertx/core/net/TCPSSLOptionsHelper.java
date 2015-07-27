package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class TCPSSLOptionsHelper {

  public static void fromJson(JsonObject json, TCPSSLOptions obj) {
    if (json.getValue("crlPaths") instanceof JsonArray) {
      json.getJsonArray("crlPaths").getList().forEach(item -> {
        if (item instanceof String)
          obj.addCrlPath((String)item);
      });
    }
    if (json.getValue("crlValues") instanceof JsonArray) {
      json.getJsonArray("crlValues").getList().forEach(item -> {
        if (item instanceof String)
          obj.addCrlValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)item)));
      });
    }
    if (json.getValue("enabledCipherSuites") instanceof JsonArray) {
      json.getJsonArray("enabledCipherSuites").getList().forEach(item -> {
        if (item instanceof String)
          obj.addEnabledCipherSuite((String)item);
      });
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
    if (json.getValue("usePooledBuffers") instanceof Boolean) {
      obj.setUsePooledBuffers((Boolean)json.getValue("usePooledBuffers"));
    }
  }

  public static void toJson(TCPSSLOptions obj, JsonObject json) {
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
    json.put("idleTimeout", obj.getIdleTimeout());
    json.put("soLinger", obj.getSoLinger());
    json.put("ssl", obj.isSsl());
    json.put("tcpKeepAlive", obj.isTcpKeepAlive());
    json.put("tcpNoDelay", obj.isTcpNoDelay());
    json.put("usePooledBuffers", obj.isUsePooledBuffers());
  }
}