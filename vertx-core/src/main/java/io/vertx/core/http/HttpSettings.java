/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

/**
 * HTTP settings, is a general settings class for http2Settings and http3Settings.<p>
 * <p>
 *
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class HttpSettings {
  private HttpVersion version;
  private Http2Settings http2Settings;
  private Http3Settings http3Settings;
  private io.netty.handler.codec.http2.Http2Settings nettyHttp2Settings;
  private Http3SettingsFrame nettyHttp3Settings;

  public HttpSettings() {
  }

  /**
   * Create a settings from JSON
   *
   * @param json the JSON
   */
  public HttpSettings(JsonObject json) {
    this();
    HttpSettingsConverter.fromJson(json, this);
  }

  public HttpSettings(Http2Settings http2Settings) {
    this.http2Settings = http2Settings;
    this.version = HttpVersion.HTTP_2;
  }

  public HttpSettings(Http3Settings http3Settings) {
    this.http3Settings = http3Settings;
    this.version = HttpVersion.HTTP_3;
  }

  public HttpSettings(io.netty.handler.codec.http2.Http2Settings settings) {
    this.nettyHttp2Settings = settings;
    this.version = HttpVersion.HTTP_2;
  }

  public HttpSettings(Http3SettingsFrame nettyHttp3Settings) {
    this.nettyHttp3Settings = nettyHttp3Settings;
    this.version = HttpVersion.HTTP_3;
  }

  public HttpSettings(HttpSettings other) {
    if (other.version == HttpVersion.HTTP_2) {
      this.http2Settings = new Http2Settings(other.http2Settings);
    }
    if (other.version == HttpVersion.HTTP_3) {
      this.http3Settings = new Http3Settings(other.http3Settings);
    }
  }

  public Http2Settings getHttp2Settings() {
    Arguments.require(version == HttpVersion.HTTP_2, "The settings is not for HTTP/2");
    return http2Settings != null ? http2Settings : HttpUtils.toVertxSettings(nettyHttp2Settings);
  }

  public Http3Settings getHttp3Settings() {
    Arguments.require(version == HttpVersion.HTTP_3, "The settings is not for HTTP/3");
    return http3Settings != null ? http3Settings : HttpUtils.toVertxSettings(nettyHttp3Settings);
  }

  public io.netty.handler.codec.http2.Http2Settings getNettyHttp2Settings() {
    Arguments.require(version == HttpVersion.HTTP_2, "The settings is not for HTTP/2");
    return nettyHttp2Settings != null ? nettyHttp2Settings : HttpUtils.fromVertxSettings(http2Settings);
  }

  public Http3SettingsFrame getNettyHttp3Settings() {
    Arguments.require(version == HttpVersion.HTTP_3, "The settings is not for HTTP/3");
    return nettyHttp3Settings != null ? nettyHttp3Settings : HttpUtils.fromVertxSettings(http3Settings);
  }

  public Long get(Character key) {
    if (version == HttpVersion.HTTP_2) {
      return http2Settings.get(key);
    }
    if (version == HttpVersion.HTTP_3) {
      return http3Settings.get(key);
    }
    return null;
  }

  public Long remove(Character key) {
    if (version == HttpVersion.HTTP_2) {
      return nettyHttp2Settings.remove(key);
    }
    if (version == HttpVersion.HTTP_3) {
      throw new RuntimeException("Not implemented");
    }
    throw new RuntimeException("Not implemented");
  }

  public void putAll(HttpSettings settingsUpdate) {
    if (version == HttpVersion.HTTP_2) {
      settingsUpdate
        .getNettyHttp2Settings()
        .forEach((key, value) -> this.getNettyHttp2Settings().put(key, value));
      return;
    }
    if (version == HttpVersion.HTTP_3) {
      settingsUpdate
        .getNettyHttp3Settings()
        .forEach(entry -> this.getNettyHttp3Settings().put(entry.getKey(), entry.getValue()));
    }
  }

  @Override
  public String toString() {
    return toJson().encode();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    HttpSettingsConverter.toJson(this, json);
    return json;
  }

}
