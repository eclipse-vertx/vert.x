/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP2 settings, the settings is initialized with the default HTTP/2 values.<p>
 *
 * The settings expose the parameters defined by the HTTP/2 specification, as well as extra settings for
 * protocol extensions.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class Http2Settings {

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
   * Default HTTP/2 spec value for {@link #getMaxHeaderListSize} : {@code Integer.MAX_VALUE}
   */
  public static final int DEFAULT_MAX_HEADER_LIST_SIZE = Integer.MAX_VALUE;

  /**
   * Default HTTP/2 spec value for {@link #getExtraSettings} : {@code null}
   */
  public static final Map<Integer, Long> DEFAULT_EXTRA_SETTINGS = null;

  private long headerTableSize;
  private boolean pushEnabled;
  private long maxConcurrentStreams;
  private int initialWindowSize;
  private int maxFrameSize;
  private long maxHeaderListSize;
  private Map<Integer, Long> extraSettings;

  /**
   * Default constructor
   */
  public Http2Settings() {
    headerTableSize = DEFAULT_HEADER_TABLE_SIZE;
    pushEnabled = DEFAULT_ENABLE_PUSH;
    maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    maxHeaderListSize = DEFAULT_MAX_HEADER_LIST_SIZE;
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
    headerTableSize = other.headerTableSize;
    pushEnabled = other.pushEnabled;
    maxConcurrentStreams = other.maxConcurrentStreams;
    initialWindowSize = other.initialWindowSize;
    maxFrameSize = other.maxFrameSize;
    maxHeaderListSize = other.maxHeaderListSize;
    extraSettings = other.extraSettings != null ? new HashMap<>(other.extraSettings) : null;
  }

  /**
   * @return the {@literal SETTINGS_HEADER_TABLE_SIZE} HTTP/2 setting
   */
  public long getHeaderTableSize() {
    return headerTableSize;
  }

  /**
   * Set {@literal SETTINGS_HEADER_TABLE_SIZE} HTTP/2 setting.
   *
   * @param headerTableSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setHeaderTableSize(long headerTableSize) {
    Arguments.require(headerTableSize >= Http2CodecUtil.MIN_HEADER_TABLE_SIZE,
        "headerTableSize must be >= " + Http2CodecUtil.MIN_HEADER_TABLE_SIZE);
    Arguments.require(headerTableSize <= Http2CodecUtil.MAX_HEADER_TABLE_SIZE,
        "headerTableSize must be <= " + Http2CodecUtil.MAX_HEADER_TABLE_SIZE);
    this.headerTableSize = headerTableSize;
    return this;
  }

  /**
   * @return the {@literal SETTINGS_ENABLE_PUSH} HTTP/2 setting
   */
  public boolean isPushEnabled() {
    return pushEnabled;
  }

  /**
   * Set the {@literal SETTINGS_ENABLE_PUSH} HTTP/2 setting
   *
   * @param pushEnabled the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setPushEnabled(boolean pushEnabled) {
    this.pushEnabled = pushEnabled;
    return this;
  }

  /**
   * @return the {@literal SETTINGS_MAX_CONCURRENT_STREAMS} HTTP/2 setting
   */
  public long getMaxConcurrentStreams() {
    return maxConcurrentStreams;
  }

  /**
   * Set the {@literal SETTINGS_MAX_CONCURRENT_STREAMS} HTTP/2 setting
   *
   * @param maxConcurrentStreams the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setMaxConcurrentStreams(long maxConcurrentStreams) {
    Arguments.require(maxConcurrentStreams >= Http2CodecUtil.MIN_CONCURRENT_STREAMS,
        "maxConcurrentStreams must be >= " + Http2CodecUtil.MIN_CONCURRENT_STREAMS);
    Arguments.require(maxConcurrentStreams <= Http2CodecUtil.MAX_CONCURRENT_STREAMS,
        "maxConcurrentStreams must be < " + Http2CodecUtil.MAX_CONCURRENT_STREAMS);
    this.maxConcurrentStreams = maxConcurrentStreams;
    return this;
  }

  /**
   * @return the {@literal SETTINGS_INITIAL_WINDOW_SIZE} HTTP/2 setting
   */
  public int getInitialWindowSize() {
    return initialWindowSize;
  }

  /**
   * Set the {@literal SETTINGS_INITIAL_WINDOW_SIZE} HTTP/2 setting
   *
   * @param initialWindowSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setInitialWindowSize(int initialWindowSize) {
    Arguments.require(initialWindowSize >= Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE,
        "initialWindowSize must be >= " + Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE);
    this.initialWindowSize = initialWindowSize;
    return this;
  }

  /**
   * @return the {@literal SETTINGS_MAX_FRAME_SIZE} HTTP/2 setting
   */
  public int getMaxFrameSize() {
    return maxFrameSize;
  }

  /**
   * Set the {@literal SETTINGS_MAX_FRAME_SIZE} HTTP/2 setting
   *
   * @param maxFrameSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setMaxFrameSize(int maxFrameSize) {
    Arguments.require(maxFrameSize >= Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND,
        "maxFrameSize must be >= " + Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
    Arguments.require(maxFrameSize <= Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND,
        "maxFrameSize must be <= " + Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND);
    this.maxFrameSize = maxFrameSize;
    return this;
  }

  /**
   * @return the {@literal SETTINGS_MAX_HEADER_LIST_SIZE} HTTP/2 setting
   */
  public long getMaxHeaderListSize() {
    return maxHeaderListSize;
  }

  /**
   * Set the {@literal SETTINGS_MAX_HEADER_LIST_SIZE} HTTP/2 setting
   *
   * @param maxHeaderListSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2Settings setMaxHeaderListSize(long maxHeaderListSize) {
    Arguments.require(maxHeaderListSize >= 0, "maxHeaderListSize must be >= 0");
    Arguments.require(maxHeaderListSize >= Http2CodecUtil.MIN_HEADER_LIST_SIZE,
        "maxHeaderListSize must be >= " + Http2CodecUtil.MIN_HEADER_LIST_SIZE);
    this.maxHeaderListSize = maxHeaderListSize;
    return this;
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
    switch (id) {
      case 1:
        return headerTableSize;
      case 2:
        return pushEnabled ? 1L : 0L;
      case 3:
        return maxConcurrentStreams;
      case 4:
        return (long)initialWindowSize;
      case 5:
        return (long)maxFrameSize;
      case 6:
        return (long)maxHeaderListSize;
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
        setHeaderTableSize(value);
        break;
      case 2:
        Arguments.require(value == 0 || value == 1, "enablePush must be 0 or 1");
        setPushEnabled(value == 1);
        break;
      case 3:
        setMaxConcurrentStreams(value);
        break;
      case 4:
        setInitialWindowSize((int) value);
        break;
      case 5:
        setMaxFrameSize((int) value);
        break;
      case 6:
        Arguments.require(value <= Integer.MAX_VALUE, "maxHeaderListSize must be <= " + Integer.MAX_VALUE);
        setMaxHeaderListSize((int) value);
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Http2Settings that = (Http2Settings) o;

    if (headerTableSize != that.headerTableSize) return false;
    if (pushEnabled != that.pushEnabled) return false;
    if (maxConcurrentStreams != that.maxConcurrentStreams) return false;
    if (initialWindowSize != that.initialWindowSize) return false;
    if (maxFrameSize != that.maxFrameSize) return false;
    if (maxHeaderListSize != that.maxHeaderListSize) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) (headerTableSize ^ (headerTableSize >>> 32));
    result = 31 * result + (pushEnabled ? 1 : 0);
    result = 31 * result + (int) (maxConcurrentStreams ^ (maxConcurrentStreams >>> 32));
    result = 31 * result + initialWindowSize;
    result = 31 * result + maxFrameSize;
    result = 31 * result + (int) (maxHeaderListSize ^ (maxHeaderListSize >>> 32));
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
