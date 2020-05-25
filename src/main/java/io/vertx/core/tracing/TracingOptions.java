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

  private JsonObject json; // Keep a copy of the original json, so we don't lose info when building options subclasses
  private VertxTracerFactory factory;

  /**
   * Default constructor
   */
  public TracingOptions() {
  }

  /**
   * Copy constructor
   *
   * @param other The other {@link TracingOptions} to copy when creating this
   */
  public TracingOptions(TracingOptions other) {
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
      "json=" + json +
      '}';
  }
}
