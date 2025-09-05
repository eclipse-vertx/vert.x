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

import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP3 settings, the settings is initialized with the default HTTP/3 values.<p>
 * <p>
 * The settings expose the parameters defined by the HTTP/3 specification, as well as extra settings for
 * protocol extensions.
 *
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class Http3Settings {

  public final static Set<Long> VALID_H3_SETTINGS_KEYS = Set.of(
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY,
    Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE,
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS
  );

  public final static long HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08;
  public final static long HTTP3_SETTINGS_H3_DATAGRAM = 0x33;
  public final static long HTTP3_SETTINGS_ENABLE_METADATA = 0x4d44;

  /**
   * Default HTTP/3 spec value for {@link #getQpackMaxTableCapacity} : {@code 4096}
   */
  public static final long DEFAULT_QPACK_MAX_TABLE_CAPACITY = 4096;
  /**
   * Default HTTP/3 spec value for {@link #getMaxFieldSectionSize} : {@code 16384}
   */
  public static final long DEFAULT_MAX_FIELD_SECTION_SIZE = 16384;
  /**
   * Default HTTP/3 spec value for {@link #getQpackMaxBlockedStreams} : {@code 256}
   */
  public static final long DEFAULT_QPACK_BLOCKED_STREAMS = 256;
  /**
   * Default HTTP/3 spec value for {@link #getEnableConnectProtocol} : {@code 0}
   */
  public static final long DEFAULT_ENABLE_CONNECT_PROTOCOL = 0;
  /**
   * Default HTTP/3 spec value for {@link #getH3Datagram} : {@code 1}
   */
  public static final long DEFAULT_H3_DATAGRAM = 1;
  /**
   * Default HTTP/3 spec value for {@link #getEnableMetadata} : {@code 0}
   */
  public static final long DEFAULT_ENABLE_METADATA = 0;

  public static final Map<Long, Long> DEFAULT_EXTRA_SETTINGS = null;

  public static final Set<Long> SETTING_KEYS = Set.of(
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY,
    Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE,
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS,
    HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL,
    HTTP3_SETTINGS_H3_DATAGRAM,
    HTTP3_SETTINGS_ENABLE_METADATA
  );


  private long qpackMaxTableCapacity;
  private long maxFieldSectionSize;
  private long qpackMaxBlockedStreams;
  private long enableConnectProtocol;
  private long h3Datagram;
  private long enableMetadata;


  private Map<Long, Long> extraSettings;

  /**
   * Default constructor
   */
  public Http3Settings() {
    qpackMaxTableCapacity = DEFAULT_QPACK_MAX_TABLE_CAPACITY;
    maxFieldSectionSize = DEFAULT_MAX_FIELD_SECTION_SIZE;
    qpackMaxBlockedStreams = DEFAULT_QPACK_BLOCKED_STREAMS;
    enableConnectProtocol = DEFAULT_ENABLE_CONNECT_PROTOCOL;
    h3Datagram = DEFAULT_H3_DATAGRAM;
    enableMetadata = DEFAULT_ENABLE_METADATA;

    extraSettings = DEFAULT_EXTRA_SETTINGS;
  }

  /**
   * Create a settings from JSON
   *
   * @param json the JSON
   */
  public Http3Settings(JsonObject json) {
    this();
    Http3SettingsConverter.fromJson(json, this);
  }

  /**
   * Copy constructor
   *
   * @param other the settings to copy
   */
  public Http3Settings(Http3Settings other) {
    qpackMaxTableCapacity = other.qpackMaxTableCapacity;
    maxFieldSectionSize = other.maxFieldSectionSize;
    qpackMaxBlockedStreams = other.qpackMaxBlockedStreams;
    enableConnectProtocol = other.enableConnectProtocol;
    h3Datagram = other.h3Datagram;
    enableMetadata = other.enableMetadata;
    extraSettings = other.extraSettings != null ? new HashMap<>(other.extraSettings) : null;
  }

  /**
   * @return the {@literal HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY} HTTP/3 setting
   */
  public long getQpackMaxTableCapacity() {
    return qpackMaxTableCapacity;
  }

  /**
   * Set the {@literal HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY} HTTP/3 setting
   *
   * @param qpackMaxTableCapacity the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setQpackMaxTableCapacity(long qpackMaxTableCapacity) {
    this.qpackMaxTableCapacity = qpackMaxTableCapacity;
    return this;
  }

  /**
   * @return the {@literal HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE} HTTP/3 setting
   */
  public long getMaxFieldSectionSize() {
    return maxFieldSectionSize;
  }

  /**
   * Set the {@literal HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE} HTTP/3 setting
   *
   * @param maxFieldSectionSize the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setMaxFieldSectionSize(long maxFieldSectionSize) {
    this.maxFieldSectionSize = maxFieldSectionSize;
    return this;
  }

  /**
   * @return the {@literal HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS} HTTP/3 setting
   */
  public long getQpackMaxBlockedStreams() {
    return qpackMaxBlockedStreams;
  }

  /**
   * Set the {@literal HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS} HTTP/3 setting
   *
   * @param qpackMaxBlockedStreams the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setQpackMaxBlockedStreams(long qpackMaxBlockedStreams) {
    this.qpackMaxBlockedStreams = qpackMaxBlockedStreams;
    return this;
  }

  /**
   * @return the {@literal HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL} HTTP/3 setting
   */
  public long getEnableConnectProtocol() {
    return enableConnectProtocol;
  }

  /**
   * Set the {@literal HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL} HTTP/3 setting
   *
   * @param enableConnectProtocol the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setEnableConnectProtocol(long enableConnectProtocol) {
    this.enableConnectProtocol = enableConnectProtocol;
    return this;
  }

  /**
   * @return the {@literal HTTP3_SETTINGS_H3_DATAGRAM} HTTP/3 setting
   */
  public long getH3Datagram() {
    return h3Datagram;
  }

  /**
   * Set the {@literal HTTP3_SETTINGS_H3_DATAGRAM} HTTP/3 setting
   *
   * @param h3Datagram the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setH3Datagram(long h3Datagram) {
    this.h3Datagram = h3Datagram;
    return this;
  }

  /**
   * @return the {@literal HTTP3_SETTINGS_ENABLE_METADATA} HTTP/3 setting
   */
  public long getEnableMetadata() {
    return enableMetadata;
  }

  /**
   * Set the {@literal HTTP3_SETTINGS_ENABLE_METADATA} HTTP/3 setting
   *
   * @param enableMetadata the new value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setEnableMetadata(long enableMetadata) {
    this.enableMetadata = enableMetadata;
    return this;
  }


  /**
   * @return the extra settings used for extending HTTP/3
   */
  @GenIgnore
  public Map<Long, Long> getExtraSettings() {
    return extraSettings;
  }

  /**
   * Set the extra setting used for extending HTTP/3
   *
   * @param settings the new extra settings
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public Http3Settings setExtraSettings(Map<Long, Long> settings) {
    extraSettings = settings;
    return this;
  }

  /**
   * Return a setting value according to its identifier.
   *
   * @param id the setting identifier
   * @return the setting value
   */
  public Long get(long id) {
    switch (Math.toIntExact(id)) {
      case (int) Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY:
        return qpackMaxTableCapacity;
      case (int) Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE:
        return maxFieldSectionSize;
      case (int) Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS:
        return qpackMaxBlockedStreams;
      case (int) HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL:
        return enableConnectProtocol;
      case (int) HTTP3_SETTINGS_H3_DATAGRAM:
        return h3Datagram;
      case (int) HTTP3_SETTINGS_ENABLE_METADATA:
        return enableMetadata;

      default:
        return extraSettings != null ? extraSettings.get(id) : null;
    }
  }

  /**
   * Set a setting {@code value} for a given setting {@code id}.
   *
   * @param id    the setting id
   * @param value the setting value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings set(long id, long value) {
    Arguments.require(id >= 0 && id <= 0xFFFF, "Setting id must me an unsigned 16-bit value");
    Arguments.require(value >= 0L && value <= 0xFFFFFFFFL, "Setting value must me an unsigned 32-bit value");
    switch ((int) id) {
      case (int) Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY:
        setQpackMaxTableCapacity(value);
        break;
      case (int) Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE:
        setMaxFieldSectionSize(value);
        break;
      case (int) Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS:
        setQpackMaxBlockedStreams(value);
        break;
      case (int) HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL:
        setEnableConnectProtocol(value);
        break;
      case (int) HTTP3_SETTINGS_H3_DATAGRAM:
        setH3Datagram(value);
        break;
      case (int) HTTP3_SETTINGS_ENABLE_METADATA:
        setEnableMetadata(value);
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

    Http3Settings that = (Http3Settings) o;

    if (qpackMaxTableCapacity != that.qpackMaxTableCapacity) return false;
    if (maxFieldSectionSize != that.maxFieldSectionSize) return false;
    if (qpackMaxBlockedStreams != that.qpackMaxBlockedStreams) return false;
    if (enableConnectProtocol != that.enableConnectProtocol) return false;
    if (h3Datagram != that.h3Datagram) return false;
    if (enableMetadata != that.enableMetadata) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(qpackMaxTableCapacity, maxFieldSectionSize, qpackMaxBlockedStreams, enableConnectProtocol,
      h3Datagram, enableMetadata);
  }

  @Override
  public String toString() {
    return toJson().encode();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    Http3SettingsConverter.toJson(this, json);
    return json;
  }
}
