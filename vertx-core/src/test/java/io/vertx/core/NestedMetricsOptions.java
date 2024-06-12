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

package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;

/**
 * @author Thomas Segismont
 */
public class NestedMetricsOptions extends MetricsOptions {

  private String nestedProperty;

  public NestedMetricsOptions() {
  }

  public NestedMetricsOptions(JsonObject jsonObject) {
    nestedProperty = jsonObject.getString("nestedProperty");
  }

  public String getNestedProperty() {
    return nestedProperty;
  }

  public void setNestedProperty(String nestedProperty) {
    this.nestedProperty = nestedProperty;
  }
}
