/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingPolicy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Thomas Segismont
 */
public class DeliveryOptionsTest {

  @Test
  public void toJson() throws Exception {
    JsonObject defaultJson = new JsonObject()
      .put("timeout", DeliveryOptions.DEFAULT_TIMEOUT)
      .put("localOnly", DeliveryOptions.DEFAULT_LOCAL_ONLY)
      .put("tracingPolicy", DeliveryOptions.DEFAULT_TRACING_POLICY);
    assertEquals(defaultJson, new DeliveryOptions().toJson());

    JsonObject fullJson = new JsonObject()
      .put("timeout", 15000)
      .put("localOnly", true)
      .put("codecName", "pimpo")
      .put("headers", new JsonObject().put("marseille", "om").put("lyon", "ol").put("amsterdam", "ajax"))
      .put("tracingPolicy", "IGNORE");

    assertEquals(fullJson,
      new DeliveryOptions()
        .setSendTimeout(15000)
        .setLocalOnly(true)
        .setCodecName("pimpo")
        .addHeader("marseille", "om").addHeader("lyon", "ol").addHeader("amsterdam", "ajax")
        .setTracingPolicy(TracingPolicy.IGNORE)
        .toJson());

    assertEquals(fullJson, new DeliveryOptions(fullJson).toJson());
  }
}
