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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;

/**
 * Vert.x tracing base configuration, this class can be extended by provider implementations to configure
 * those specific implementations.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class TracingOptions {

  private JsonObject json; // Keep a copy of the original json, so we don't lose info when building options subclasses

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
    json = other.json;
    if (json != null) {
      json = json.copy();
    }
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
