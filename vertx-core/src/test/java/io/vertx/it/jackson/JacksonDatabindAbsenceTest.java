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

package io.vertx.it.jackson;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonDatabindAbsenceTest {

  @Test
  public void testJsonObject() {
    JsonObject obj = new JsonObject("{\"foo\":\"bar\"}");
    assertEquals("bar", obj.getString("foo"));
    assertEquals("{\"foo\":\"bar\"}", obj.toString());
    try {
      obj.mapTo(Object.class);
      fail();
    } catch (DecodeException ignore) {
      // Expected
    }
  }
}
