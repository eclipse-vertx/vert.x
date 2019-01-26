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
import io.vertx.core.spi.TracerFactory;
import io.vertx.core.spi.tracing.Tracer;

@DataObject
public class TracingOptions {

  public static final TracerFactory DEFAULT_TRACER_FACTORY = Tracer.factory;

  private TracerFactory factory = DEFAULT_TRACER_FACTORY;

  public TracingOptions() {
  }

  public TracingOptions(JsonObject json) {
  }

  public TracingOptions(TracingOptions other) {
    this.factory = other.factory;
  }

  public TracerFactory getFactory() {
    return factory;
  }

  public TracingOptions setFactory(TracerFactory factory) {
    this.factory = factory;
    return this;
  }

  public TracingOptions copy() {
    return new TracingOptions(this);
  }
}
