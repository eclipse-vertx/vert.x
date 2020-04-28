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

package io.vertx.core.json;

import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonTest extends VertxTestBase {

  private final JacksonCodec codec = new JacksonCodec();

  public static class MyPojo {
  }

  @Test
  public void testEncodeUnknownNumber() {
    String result = codec.toString(new Number() {
      @Override
      public int intValue() {
        throw new UnsupportedOperationException();
      }
      @Override
      public long longValue() {
        throw new UnsupportedOperationException();
      }
      @Override
      public float floatValue() {
        throw new UnsupportedOperationException();
      }
      @Override
      public double doubleValue() {
        return 4D;
      }
    });
    assertEquals("4.0", result);
  }

  @Test
  public void testEncodePojoFailure() {
    try {
      codec.toString(new MyPojo());
      fail();
    } catch (EncodeException e) {
      assertTrue(e.getMessage().contains(MyPojo.class.getName()));
    }
  }
}
