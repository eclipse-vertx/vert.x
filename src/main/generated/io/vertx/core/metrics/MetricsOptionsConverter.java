/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.metrics;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.metrics.MetricsOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.metrics.MetricsOptions} original class using Vert.x codegen.
 */
public class MetricsOptionsConverter {

  public static void fromJson(JsonObject json, MetricsOptions obj) {
    if (json.getValue("enabled") instanceof Boolean) {
      obj.setEnabled((Boolean)json.getValue("enabled"));
    }
  }

  public static void toJson(MetricsOptions obj, JsonObject json) {
    json.put("enabled", obj.isEnabled());
  }
}
