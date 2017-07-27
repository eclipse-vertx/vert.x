/*
 * Copyright (c) 2011-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class DeliveryOptionsTest {

  @Test
  public void toJson() throws Exception {
    JsonObject defaultJson = new JsonObject().put("timeout", DeliveryOptions.DEFAULT_TIMEOUT);
    assertEquals(defaultJson, new DeliveryOptions().toJson());

    JsonObject fullJson = new JsonObject()
      .put("timeout", 15000)
      .put("codecName", "pimpo")
      .put("headers", new JsonObject().put("marseille", "om").put("lyon", "ol").put("amsterdam", "ajax"));

    assertEquals(fullJson,
      new DeliveryOptions()
        .setSendTimeout(15000)
        .setCodecName("pimpo")
        .addHeader("marseille", "om").addHeader("lyon", "ol").addHeader("amsterdam", "ajax")
        .toJson());

    assertEquals(fullJson, new DeliveryOptions(fullJson).toJson());
  }
}
