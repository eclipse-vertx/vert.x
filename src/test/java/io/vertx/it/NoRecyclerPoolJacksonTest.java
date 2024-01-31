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

package io.vertx.it;

import com.fasterxml.jackson.core.Version;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NoRecyclerPoolJacksonTest extends VertxTestBase {

  @Test
  public void testJsonObject() {

    Version version = com.fasterxml.jackson.core.json.PackageVersion.VERSION;
    assertEquals("2.15.1", version.toString());

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
