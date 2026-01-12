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

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * HTTP2 settings, the settings is initialized with the default HTTP/2 values.<p>
 *
 * The settings expose the parameters defined by the HTTP/2 specification, as well as extra settings for
 * protocol extensions.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public final class Http2Settings extends HttpSettings {

  /**
   * Default HTTP/2 spec value for {@link #getHeaderTableSize} : {@code 4096}
   */
  public static final long DEFAULT_HEADER_TABLE_SIZE = 4096;

  /**
   * Default HTTP/2 spec value for {@link #isPushEnabled} : {@code true}
   */
  public static final boolean DEFAULT_ENABLE_PUSH = true;

  /**
   * Default HTTP/2 spec value for {@link #getMaxConcurrentStreams} : {@code 0xFFFFFFFFL}
   */
  public static final long DEFAULT_MAX_CONCURRENT_STREAMS = 0xFFFFFFFFL;

  /**
   * Default HTTP/2 spec value for {@link #getInitialWindowSize} : {@code 65535}
   */
  public static final int DEFAULT_INITIAL_WINDOW_SIZE = 65535;

  /**
   * Default HTTP/2 spec value for {@link #getMaxFrameSize} : {@code 16384}
   */
  public static final int DEFAULT_MAX_FRAME_SIZE = 16384;

  /**
   * Default HTTP/2 spec value for {@link #getMaxHeaderListSize} : {@code 8192}
   */
  public static final int DEFAULT_MAX_HEADER_LIST_SIZE = 8192;

  /**
   * Default HTTP/2 spec value for {@link #getExtraSettings} : {@code null}
   */
  public static final Map<Integer, Long> DEFAULT_EXTRA_SETTINGS = null;

  /**
   * HTTP/2 {@code HEADER_TABLE_SIZE} setting
   */
  public static final HttpSetting<Long> HEADER_TABLE_SIZE;

  /**
   * HTTP/2 {@code HEADER_TABLE_SIZE} setting
   */
  public static final HttpSetting<Boolean> ENABLE_PUSH;

  /**
   * HTTP/2 {@code MAX_CONCURRENT_STREAMS} setting
   */
  public static final HttpSetting<Long> MAX_CONCURRENT_STREAMS;

  /**
   * HTTP/2 {@code INITIAL_WINDOW_SIZE} setting
   */
  public static final HttpSetting<Integer> INITIAL_WINDOW_SIZE;

  /**
   * HTTP/2 {@code MAX_FRAME_SIZE} setting
   */
  public static final HttpSetting<Integer> MAX_FRAME_SIZE;

  /**
   * HTTP/2 {@code MAX_HEADER_LIST_SIZE} setting
   */
  public static final HttpSetting<Long> MAX_HEADER_LIST_SIZE;

  static {

    EnumSet<HttpVersion> baseVersions = EnumSet.of(HttpVersion.HTTP_2);

    LongConsumer headerTableSizeValidator = value -> {
      Arguments.require(value >= Http2CodecUtil.MIN_HEADER_TABLE_SIZE,
        "headerTableSize must be >= " + Http2CodecUtil.MIN_HEADER_TABLE_SIZE);
      Arguments.require(value <= Http2CodecUtil.MAX_HEADER_TABLE_SIZE,
        "headerTableSize must be <= " + Http2CodecUtil.MAX_HEADER_TABLE_SIZE);
    };
    HEADER_TABLE_SIZE = new HttpSetting<>(0x01, "HEADER_TABLE_SIZE", 4096L,
      val -> val, val -> val, headerTableSizeValidator, baseVersions);

    LongConsumer enablePushValidator = value -> {
      Arguments.require(value == 0 || value == 1, "enablePush must be 0 or 1");
    };
    ENABLE_PUSH = new HttpSetting<>(0x02, "ENABLE_PUSH", true,
      val -> val == null ? 1L : (val ? 1 : 0), val -> val == 1L, enablePushValidator, baseVersions);

    LongConsumer maxConcurrentStreamsValidator = value -> {
      Arguments.require(value >= Http2CodecUtil.MIN_CONCURRENT_STREAMS,
        "value must be >= " + Http2CodecUtil.MIN_CONCURRENT_STREAMS);
      Arguments.require(value <= Http2CodecUtil.MAX_CONCURRENT_STREAMS,
        "value must be <= " + Http2CodecUtil.MAX_CONCURRENT_STREAMS);
    };
    MAX_CONCURRENT_STREAMS = new HttpSetting<>(0x03, "MAX_CONCURRENT_STREAMS", 0xFFFFFFFFL,
      val -> val, val -> val, maxConcurrentStreamsValidator, baseVersions);

    LongConsumer initialWindowSizeValidator = value -> {
      Arguments.require(value >= Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE,
        "value must be >= " + Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE);
      Arguments.require(value <= Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE,
        "value must be <= " + Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE);
    };
    INITIAL_WINDOW_SIZE = new HttpSetting<>(0x04, "INITIAL_WINDOW_SIZE", 65535,
      val -> val, val -> (int)val, initialWindowSizeValidator, baseVersions);

    LongConsumer maxFrameSizeValidator = value -> {
      Arguments.require(value >= Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND,
        "value must be >= " + Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
      Arguments.require(value <= Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND,
        "value must be <= " + Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND);
    };
    MAX_FRAME_SIZE = new HttpSetting<>(0x05, "MAX_FRAME_SIZE", 16384,
      val -> val, val -> (int)val, maxFrameSizeValidator, baseVersions);

    LongConsumer maxHeaderListSizeValidator = value -> {
      Arguments.require(value >= Http2CodecUtil.MIN_HEADER_LIST_SIZE,
        "maxHeaderListSize must be >= " + Http2CodecUtil.MIN_HEADER_LIST_SIZE);
      Arguments.require(value <= Http2CodecUtil.MAX_HEADER_LIST_SIZE, "value must be <= " + Http2CodecUtil.MAX_HEADER_LIST_SIZE);
    };
    MAX_HEADER_LIST_SIZE = new HttpSetting<>(0x06, "MAX_HEADER_LIST_SIZE", 8192L,
      val -> val, val -> val, maxHeaderListSizeValidator, baseVersions);
  }

  private Map<Integer, Long> extraSettings;

  /**
   * Default constructor
   */
  public Http2Settings() {
    super(7);
    extraSettings = DEFAULT_EXTRA_SETTINGS;
  }

  /**
   * Create an settings from JSON
   *
   * @param json the JSON
   */
  public Http2Settings(JsonObject json) {
    this();
    Http2SettingsConverter.fromJson(json, this);
  }

  /**
   * Copy constructor
   *
   * @param other the settings to copy
   */
  public Http2Settings(Http2Settings other) {
    super(other);
    extraSettings = other.extraSettings != null ? new HashMap<>(other.extraSettings) : null;
  }

  @Override
  HttpVersion version() {
    return HttpVersion.HTTP_2;
  }

  /**
   * @return the {@literal SETTINGS_HEADER_TABLE_SIZE} HTTP/2 setting
   */
  public long getHeaderTableSize() {
    return getValueOrDefault(HEADER_TABLE_SIZE);
  }

  /**
   * Set {@literal SETTINGS_HEADER_TABLE_SIZE} HTTP/2 setting.
   *
   * @param headerTableSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setHeaderTableSize(long headerTableSize) {
    setRaw(HEADER_TABLE_SIZE, headerTableSize);
    return this;
  }

  /**
   * @return the {@literal SETTINGS_ENABLE_PUSH} HTTP/2 setting
   */
  public boolean isPushEnabled() {
    return getValueOrDefault(ENABLE_PUSH);
  }

  /**
   * Set the {@literal SETTINGS_ENABLE_PUSH} HTTP/2 setting
   *
   * @param pushEnabled the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setPushEnabled(boolean pushEnabled) {
    return setValue(ENABLE_PUSH, pushEnabled);
  }

  /**
   * @return the {@literal SETTINGS_MAX_CONCURRENT_STREAMS} HTTP/2 setting
   */
  public long getMaxConcurrentStreams() {
    return getValueOrDefault(MAX_CONCURRENT_STREAMS);
  }

  /**
   * Set the {@literal SETTINGS_MAX_CONCURRENT_STREAMS} HTTP/2 setting
   *
   * @param maxConcurrentStreams the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setMaxConcurrentStreams(long maxConcurrentStreams) {
    return setRaw(MAX_CONCURRENT_STREAMS, maxConcurrentStreams);
  }

  /**
   * @return the {@literal SETTINGS_INITIAL_WINDOW_SIZE} HTTP/2 setting
   */
  public int getInitialWindowSize() {
    return getValueOrDefault(INITIAL_WINDOW_SIZE);
  }

  /**
   * Set the {@literal SETTINGS_INITIAL_WINDOW_SIZE} HTTP/2 setting
   *
   * @param initialWindowSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setInitialWindowSize(int initialWindowSize) {
    return setValue(INITIAL_WINDOW_SIZE, initialWindowSize);
  }

  /**
   * @return the {@literal SETTINGS_MAX_FRAME_SIZE} HTTP/2 setting
   */
  public int getMaxFrameSize() {
    return getValueOrDefault(MAX_FRAME_SIZE);
  }

  /**
   * Set the {@literal SETTINGS_MAX_FRAME_SIZE} HTTP/2 setting
   *
   * @param maxFrameSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setMaxFrameSize(int maxFrameSize) {
    return setValue(MAX_FRAME_SIZE, maxFrameSize);
  }

  /**
   * @return the {@literal SETTINGS_MAX_HEADER_LIST_SIZE} HTTP/2 setting
   */
  public long getMaxHeaderListSize() {
    return getValueOrDefault(MAX_HEADER_LIST_SIZE);
  }

  /**
   * Set the {@literal SETTINGS_MAX_HEADER_LIST_SIZE} HTTP/2 setting
   *
   * @param maxHeaderListSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setMaxHeaderListSize(long maxHeaderListSize) {
    return setValue(MAX_HEADER_LIST_SIZE, maxHeaderListSize);
  }

  /**
   * @return the extra settings used for extending HTTP/2
   */
  @GenIgnore
  public Map<Integer, Long> getExtraSettings() {
    return extraSettings;
  }

  /**
   * Set the extra setting used for extending HTTP/2
   *
   * @param settings the new extra settings
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public Http2Settings setExtraSettings(Map<Integer, Long> settings) {
    extraSettings = settings;
    return this;
  }

  /**
   * Return a setting value according to its identifier.
   *
   * @param id the setting identifier
   * @return the setting value
   */
  public Long get(int id) {
    // Use static array
    switch (id) {
      case 1:
        return getRawOrDefault(HEADER_TABLE_SIZE);
      case 2:
        return getRawOrDefault(ENABLE_PUSH);
      case 3:
        return getRawOrDefault(MAX_CONCURRENT_STREAMS);
      case 4:
        return getRawOrDefault(INITIAL_WINDOW_SIZE);
      case 5:
        return getRawOrDefault(MAX_FRAME_SIZE);
      case 6:
        return getRawOrDefault(MAX_HEADER_LIST_SIZE);
      default:
        return extraSettings != null ? extraSettings.get(id) : null;
    }
  }

  /**
   * Set a setting {@code value} for a given setting {@code id}.
   *
   * @param id the setting id
   * @param value the setting value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings set(int id, long value) {
    Arguments.require(id >= 0 && id <= 0xFFFF, "Setting id must me an unsigned 16-bit value");
    Arguments.require(value >= 0L && value <= 0xFFFFFFFFL, "Setting value must me an unsigned 32-bit value");
    switch (id) {
      case 1:
        setRaw(HEADER_TABLE_SIZE, value);
        break;
      case 2:
        setRaw(ENABLE_PUSH, value);
        break;
      case 3:
        setRaw(MAX_CONCURRENT_STREAMS, value);
        break;
      case 4:
        setRaw(INITIAL_WINDOW_SIZE, value);
        break;
      case 5:
        setRaw(MAX_FRAME_SIZE, value);
        break;
      case 6:
        setRaw(MAX_HEADER_LIST_SIZE, value);
        break;
      default:
        if (extraSettings == null) {
          extraSettings = new HashMap<>();
        }
        extraSettings.put(id, value);
    }
    return this;
  }

  @Override
  public <T> Http2Settings setValue(HttpSetting<T> setting, T value) {
    return (Http2Settings)super.setValue(setting, value);
  }

  @Override
  public Http2Settings setRaw(HttpSetting<?> setting, long value) {
    return (Http2Settings)super.setRaw(setting, value);
  }

  @Override
  public boolean equals(Object o) {

    // Reimplement in super class

    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Http2Settings that = (Http2Settings) o;

    if (getHeaderTableSize() != that.getHeaderTableSize()) return false;
    if (isPushEnabled() != that.isPushEnabled()) return false;
    if (getMaxConcurrentStreams() != that.getMaxConcurrentStreams()) return false;
    if (getInitialWindowSize() != that.getInitialWindowSize()) return false;
    if (getMaxFrameSize() != that.getMaxFrameSize()) return false;
    if (getMaxHeaderListSize() != that.getMaxHeaderListSize()) return false;
    return true;
  }

  @Override
  public int hashCode() {
    long headerTableSize = getHeaderTableSize();
    int result = (int) (headerTableSize ^ (headerTableSize >>> 32));
    result = 31 * result + (isPushEnabled() ? 1 : 0);
    result = 31 * result + (int) (getMaxConcurrentStreams() ^ (getMaxConcurrentStreams() >>> 32));
    result = 31 * result + getInitialWindowSize();
    result = 31 * result + getMaxFrameSize();
    result = 31 * result + (int) (getMaxHeaderListSize() ^ (getMaxHeaderListSize() >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return toJson().encode();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    Http2SettingsConverter.toJson(this, json);
    return json;
  }
}
