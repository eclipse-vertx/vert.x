/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.vertx.core.tracing.TracingPolicy;

/**
 * Generic client/server observability config.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class ObservabilityConfig {

  private String metricsName;
  private TracingPolicy tracingPolicy;

  public ObservabilityConfig() {
    this.metricsName = null;
    this.tracingPolicy = HttpServerOptions.DEFAULT_TRACING_POLICY;
  }

  public ObservabilityConfig(ObservabilityConfig other) {
    this.metricsName = other.metricsName;
    this.tracingPolicy = other.tracingPolicy;
  }

  /**
   * @return the metrics name identifying the reported metrics.
   */
  public String getMetricsName() {
    return metricsName;
  }

  /**
   * Set the metrics name identifying the reported metrics, useful for naming the server metrics.
   *
   * @param metricsName the metrics name
   * @return a reference to this, so the API can be used fluently
   */
  public ObservabilityConfig setMetricsName(String metricsName) {
    this.metricsName = metricsName;
    return this;
  }

  /**
   * @return the tracing policy
   */
  public TracingPolicy getTracingPolicy() {
    return tracingPolicy;
  }

  /**
   * Set the tracing policy for the server behavior when Vert.x has tracing enabled.
   *
   * @param tracingPolicy the tracing policy
   * @return a reference to this, so the API can be used fluently
   */
  public ObservabilityConfig setTracingPolicy(TracingPolicy tracingPolicy) {
    this.tracingPolicy = tracingPolicy;
    return this;
  }
}
