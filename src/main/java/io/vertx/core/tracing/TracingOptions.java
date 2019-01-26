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

package io.vertx.core.tracing;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;

/**
 * Vert.x tracing base configuration, this class can be extended by provider implementations to configure
 * those specific implementations.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class TracingOptions {

  /**
   * The default value of tracing enabled false
   */
  public static final boolean DEFAULT_TRACING_ENABLED = false;

  private boolean enabled;
  private JsonObject json; // Keep a copy of the original json, so we don't lose info when building options subclasses
  private VertxTracerFactory factory;

  /**
   * Default constructor
   */
  public TracingOptions() {
    enabled = DEFAULT_TRACING_ENABLED;
  }

  /**
   * Copy constructor
   *
   * @param other The other {@link TracingOptions} to copy when creating this
   */
  public TracingOptions(TracingOptions other) {
    enabled = other.isEnabled();
    factory = other.factory;
  }

  /**
   * Create an instance from a {@link JsonObject}
   *
   * @param json the JsonObject to create it from
   */
  public TracingOptions(JsonObject json) {
    this();
    TracingOptionsConverter.fromJson(json, this);
    this.json = json.copy();
  }

  /**
   * Will tracing be enabled on the Vert.x instance?
   *
   * @return true if enabled, false if not.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Set whether tracing will be enabled on the Vert.x instance.
   *
   * @param enable true if tracing enabled, or false if not.
   * @return a reference to this, so the API can be used fluently
   */
  public TracingOptions setEnabled(boolean enable) {
    this.enabled = enable;
    return this;
  }

  /**
   * Get the tracer factory to be used when tracing are enabled.
   * <p>
   * If the tracer factory has been programmatically set here, then that will be used when tracing are enabled
   * for creating the {@link io.vertx.core.spi.tracing.VertxTracer} instance.
   * <p>
   * Otherwise Vert.x attempts to locate a tracer factory implementation on the classpath.
   *
   * @return the tracer factory
   */
  public VertxTracerFactory getFactory() {
    return factory;
  }

  /**
   * Programmatically set the tracer factory to be used when tracing are enabled.
   * <p>
   * Only valid if {@link TracingOptions#isEnabled} = true.
   * <p>
   * Normally Vert.x will look on the classpath for a tracer factory implementation, but if you want to set one
   * programmatically you can use this method.
   *
   * @param factory the tracer factory
   * @return a reference to this, so the API can be used fluently
   */
  public TracingOptions setFactory(VertxTracerFactory factory) {
    this.factory = factory;
    return this;
  }

  public TracingOptions copy() {
    return new TracingOptions(this);
  }

  public JsonObject toJson() {
    return json != null ? json.copy() : new JsonObject();
  }

  @Override
  public String toString() {
    return "TracingOptions{" +
      "enabled=" + enabled +
      ", json=" + json +
      '}';
  }
}
