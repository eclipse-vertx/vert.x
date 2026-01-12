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

import java.util.Arrays;

/**
 * Base container for HTTP settings.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public abstract class HttpSettings {

  private long presence; // Could be first element of values
  private final long[] values;

  HttpSettings(int size) {
    this.values = new long[size];
  }

  HttpSettings(HttpSettings other) {
    this.presence = other.presence;
    this.values = Arrays.copyOf(other.values, other.values.length);
  }

  /**
   * @return the HTTP version defining the settings (which defines the implicit schema).
   */
  abstract HttpVersion version();

  /**
   * Set the {@code setting } value with {@code <T>}.
   *
   * @param setting the setting
   * @param value the java value
   * @return a reference to this, so the API can be used fluently
   */
  public <T> HttpSettings set(HttpSetting<T> setting, T value) {
    if (value == null) {
      throw new NullPointerException("No null value accepted");
    }
    return setLong(setting, setting.to.convert(value));
  }

  /**
   * Get the {@code setting } value as {@code <T>}.
   *
   * @param setting the setting
   * @return the value or {@code null} if the value is not present
   */
  public <T> T get(HttpSetting<T> setting) {
    HttpVersion version = version();
    if (!setting.versions.contains(version)) {
      throw new IllegalArgumentException("Invalid setting");
    }
    long value = getLongOrDefault(setting);
    return setting.from.convert(value);
  }

  /**
   * Get the {@code setting } value as {@code <T>}.
   *
   * @param setting the setting
   * @return the value, which is never {@code null} since the setting default value will be returned instead
   */
  public <T> T getOrDefault(HttpSetting<T> setting) {
    HttpVersion version = version();
    if (!setting.versions.contains(version)) {
      throw new IllegalArgumentException("Invalid setting");
    }
    long value = getLongOrDefault(setting);
    return setting.from.convert(value);
  }

  /**
   * Set the {@code setting } value with {@code long}.
   *
   * @param setting the setting
   * @param value the raw value
   * @return a reference to this, so the API can be used fluently
   */
  public HttpSettings setLong(HttpSetting<?> setting, long value) {
    HttpVersion version = version();
    if (!setting.versions.contains(version)) {
      throw new IllegalArgumentException("Invalid setting");
    }
    setting.validator.accept(value);
    presence |= 1L << setting.identifier;
    values[setting.identifier] = value;
    return this;
  }

  /**
   * Get the {@code setting } value as {@code long}.
   *
   * @param setting the setting
   * @return the raw value, it could be the default value
   */
  public long getLongOrDefault(HttpSetting<?> setting) {
    HttpVersion version = version();
    if (!setting.versions.contains(version)) {
      throw new IllegalArgumentException("Invalid setting");
    }
    if ((presence & 1L << setting.identifier) == 0) {
      return setting.defaultValue;
    } else {
      return values[setting.identifier];
    }
  }

  /**
   * Get the {@code setting } value as a {@link Long}.
   *
   * @param setting the setting
   * @return the raw value or {@code null} when the value is not set
   */
  public Long getLong(HttpSetting<?> setting) {
    HttpVersion version = version();
    if (!setting.versions.contains(version)) {
      throw new IllegalArgumentException("Invalid setting");
    }
    if ((presence & 1L << setting.identifier) == 0) {
      return null;
    } else {
      return values[setting.identifier];
    }
  }
}
