/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.metrics;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxMetricsFactory;

/**
 * Vert.x metrics base configuration, this class can be extended by provider implementations to configure
 * those specific implementations.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class MetricsOptions {

  /**
   * The default value of metrics enabled false
   */
  public static final boolean DEFAULT_METRICS_ENABLED = false;

  private boolean enabled;
  private JsonObject json; // Keep a copy of the original json, so we don't lose info when building options subclasses
  private VertxMetricsFactory factory;

  /**
   * Default constructor
   */
  public MetricsOptions() {
    enabled = DEFAULT_METRICS_ENABLED;
  }

  /**
   * Copy constructor
   *
   * @param other The other {@link MetricsOptions} to copy when creating this
   */
  public MetricsOptions(MetricsOptions other) {
    enabled = other.isEnabled();
    factory = other.factory;
  }

  /**
   * Create an instance from a {@link io.vertx.core.json.JsonObject}
   *
   * @param json the JsonObject to create it from
   */
  public MetricsOptions(JsonObject json) {
    this();
    MetricsOptionsConverter.fromJson(json, this);
    this.json = json.copy();
  }

  /**
   * Will metrics be enabled on the Vert.x instance?
   *
   * @return true if enabled, false if not.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Set whether metrics will be enabled on the Vert.x instance.
   *
   * @param enable true if metrics enabled, or false if not.
   * @return a reference to this, so the API can be used fluently
   */
  public MetricsOptions setEnabled(boolean enable) {
    this.enabled = enable;
    return this;
  }

  /**
   * Get the metrics factory to be used when metrics are enabled.
   * <p>
   * If the metrics factory has been programmatically set here, then that will be used when metrics are enabled
   * for creating the {@link io.vertx.core.spi.metrics.VertxMetrics} instance.
   * <p>
   * Otherwise Vert.x attempts to locate a metrics factory implementation on the classpath.
   *
   * @return the metrics factory
   */
  public VertxMetricsFactory getFactory() {
    return factory;
  }

  /**
   * Programmatically set the metrics factory to be used when metrics are enabled.
   * <p>
   * Only valid if {@link MetricsOptions#isEnabled} = true.
   * <p>
   * Normally Vert.x will look on the classpath for a metrics factory implementation, but if you want to set one
   * programmatically you can use this method.
   *
   * @param factory the metrics factory
   * @return a reference to this, so the API can be used fluently
   */
  public MetricsOptions setFactory(VertxMetricsFactory factory) {
    this.factory = factory;
    return this;
  }

  public JsonObject toJson() {
    return json != null ? json.copy() : new JsonObject();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MetricsOptions that = (MetricsOptions) o;

    if (enabled != that.enabled) return false;
    return !(json != null ? !json.equals(that.json) : that.json != null);

  }

  @Override
  public int hashCode() {
    int result = (enabled ? 1 : 0);
    result = 31 * result + (json != null ? json.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "MetricsOptions{" +
      "enabled=" + enabled +
      ", json=" + json +
      '}';
  }
}
