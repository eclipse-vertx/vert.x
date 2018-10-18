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

package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CustomMetricsOptions extends MetricsOptions {

  private String value;
  private NestedMetricsOptions nestedOptions;

  public CustomMetricsOptions() {
  }

  public CustomMetricsOptions(JsonObject json) {
    value = json.getString("customProperty");
    JsonObject nestedOptionsJson = json.getJsonObject("nestedOptions");
    this.nestedOptions = nestedOptionsJson == null ? null : new NestedMetricsOptions(nestedOptionsJson);
  }

  public String getCustomProperty() {
    return value;
  }

  public void setCustomProperty(String value) {
    this.value = value;
  }

  public NestedMetricsOptions getNestedOptions() {
    return nestedOptions;
  }

  public void setNestedOptions(NestedMetricsOptions nestedOptions) {
    this.nestedOptions = nestedOptions;
  }
}
