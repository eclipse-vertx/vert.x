/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

/**
 * A factory for the plug-able tracing SPI.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxTracerFactory {

  /**
   * Create a new {@link VertxTracer} object.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param options the metrics configuration option
   * @return the tracing implementation
   */
  VertxTracer tracer(TracingOptions options);

  /**
   * Create an empty tracing options.
   * Providers can override this method to provide a custom tracing options subclass that exposes custom configuration.
   * It is used by the {@link io.vertx.core.Launcher} class when creating new options when building a CLI Vert.x.
   *
   * @return new tracing options
   */
  default TracingOptions newOptions() {
    return new TracingOptions();
  }

  /**
   * Create tracing options from the provided {@code jsonObject}.
   * Providers can override this method to provide a custom tracing options subclass that exposes custom configuration.
   * It is used by the {@link io.vertx.core.Launcher} class when creating new options when building a CLI Vert.x.
   *
   * @param jsonObject json provided by the user
   * @return new tracing options
   */
  default TracingOptions newOptions(JsonObject jsonObject) {
    return new TracingOptions(jsonObject);
  }
}
