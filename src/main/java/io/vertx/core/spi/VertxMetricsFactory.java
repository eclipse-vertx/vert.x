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

package io.vertx.core.spi;

import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.metrics.VertxMetrics;

/**
 * A factory for the plugable metrics SPI.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface VertxMetricsFactory {

  /**
   * Create a new {@link io.vertx.core.spi.metrics.VertxMetrics} object.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param options the metrics configuration option
   * @return the metrics implementation
   */
  VertxMetrics metrics(VertxOptions options);

  /**
   * Create an empty metrics options.
   * Providers can override this method to provide a custom metrics options subclass that exposes custom configuration.
   * It is used by the {@link io.vertx.core.Launcher} class when creating new options when building a CLI Vert.x.
   *
   * @return new metrics options
   */
  default MetricsOptions newOptions() {
    return new MetricsOptions();
  }

  /**
   * Create metrics options from the provided {@code jsonObject}.
   * Providers can override this method to provide a custom metrics options subclass that exposes custom configuration.
   * It is used by the {@link io.vertx.core.Launcher} class when creating new options when building a CLI Vert.x.
   *
   * @param jsonObject json provided by the user
   * @return new metrics options
   */
  default MetricsOptions newOptions(JsonObject jsonObject) {
    return new MetricsOptions(jsonObject);
  }
}
