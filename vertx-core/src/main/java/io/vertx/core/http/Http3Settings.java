/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.impl.Arguments;

import java.util.EnumSet;
import java.util.function.LongConsumer;

/**
 * HTTP/3 settings, the settings is initialized with the default HTTP/3 values.<p>
 *
 * The settings expose the parameters defined by the HTTP/3 specification.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class Http3Settings extends HttpSettings {

  /**
   * HTTP/3 {@code QPACK_MAX_TABLE_CAPACITY} setting
   */
  public static final HttpSetting<Long> QPACK_MAX_TABLE_CAPACITY;

  /**
   * HTTP/3 {@code MAX_FIELD_SECTION_SIZE} setting
   */
  public static final HttpSetting<Long> MAX_FIELD_SECTION_SIZE;

  /**
   * HTTP/3 {@code QPACK_BLOCKED_STREAMS} setting
   */
  public static final HttpSetting<Long> QPACK_BLOCKED_STREAMS;

  static {
    LongConsumer qpackMaxTableCapacityValidator = value -> {
      Arguments.require(value >= 0L,
        "Value of setting QPACK_MAX_TABLE_CAPACITY must be >= 0");
    };
    QPACK_MAX_TABLE_CAPACITY = new HttpSetting<>(0x01, "QPACK_MAX_TABLE_CAPACITY", 0L,
      val -> val, val -> val, qpackMaxTableCapacityValidator, EnumSet.of(HttpVersion.HTTP_3));

    LongConsumer maxFieldSectionSizeValidator = value -> {
      Arguments.require(value >= 0L,
        "Value of setting MAX_FIELD_SECTION_SIZE must be >= 0");
    };
    MAX_FIELD_SECTION_SIZE = new HttpSetting<>(0x06, "MAX_FIELD_SECTION_SIZE", Long.MAX_VALUE,
      val -> val, val -> val, maxFieldSectionSizeValidator, EnumSet.of(HttpVersion.HTTP_3));

    LongConsumer qpackBlockedStreamsValidator = value -> {
      Arguments.require(value >= 0L,
        "Value of setting QPACK_BLOCKED_STREAMS must be >= 0");
    };
    QPACK_BLOCKED_STREAMS = new HttpSetting<>(0x07, "QPACK_BLOCKED_STREAMS", 0L,
      val -> val, val -> val, qpackMaxTableCapacityValidator, EnumSet.of(HttpVersion.HTTP_3));
  }

  public Http3Settings() {
    super(8);
  }

  public Http3Settings(Http3Settings other) {
    super(other);
  }

  @Override
  public Http3Settings copy() {
    return new Http3Settings(this);
  }

  @Override
  HttpVersion version() {
    return HttpVersion.HTTP_3;
  }

  /**
   * @return the {@code QPACK_MAX_TABLE_CAPACITY} setting value
   */
  public long getQPackMaxTableCapacity() {
    return getOrDefault(QPACK_MAX_TABLE_CAPACITY);
  }

  /**
   * <p></p>Set the {@code QPACK_MAX_TABLE_CAPACITY} setting value.</p>
   *
   * <p>Specifies the maximum value the QPACK encoder is permitted to set for the dynamic table capacity. The
   * default value is {@code 0}</p>
   *
   * @param value the setting value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setQPackMaxTableCapacity(long value) {
    return set(QPACK_MAX_TABLE_CAPACITY, value);
  }

  /**
   * @see #setMaxFieldSectionSize(long)
   * @return the {@code MAX_FIELD_SECTION_SIZE} setting value
   */
  public long getMaxFieldSectionSize() {
    return getOrDefault(MAX_FIELD_SECTION_SIZE);
  }

  /**
   * <p>Set the {@code MAX_FIELD_SECTION_SIZE} setting value.</p>
   *
   * <p>Specifies the maximum size in bytes of a header message. The default value is infinite
   * and is represented by {@link Long#MAX_VALUE} when a default value is queried.</p>
   *
   * @param value the setting value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setMaxFieldSectionSize(long value) {
    return set(MAX_FIELD_SECTION_SIZE, value);
  }

  /**
   * @return the {@code QPACK_BLOCKED_STREAMS} setting value
   */
  public long getQPackBlockedStreams() {
    return getOrDefault(QPACK_BLOCKED_STREAMS);
  }

  /**
   * <p>Set the {@code QPACK_BLOCKED_STREAMS} setting value.</p>
   *
   * <p>Specifies an upper bound on the number of streams that can be blocked by the QPACK decoder. The default
   * value is {@code 0}.</p>
   *
   * @param value the setting value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3Settings setQPackBlockedStreams(long value) {
    return set(QPACK_BLOCKED_STREAMS, value);
  }

  @Override
  public <T> Http3Settings set(HttpSetting<T> setting, T value) {
    return (Http3Settings)super.set(setting, value);
  }

  @Override
  public Http3Settings setLong(HttpSetting<?> setting, long value) {
    return (Http3Settings)super.setLong(setting, value);
  }
}
