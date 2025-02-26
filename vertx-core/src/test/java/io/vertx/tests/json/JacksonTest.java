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

package io.vertx.tests.json;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assert;
import org.junit.Test;

import static com.fasterxml.jackson.core.StreamReadConstraints.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

  @Test(expected = EncodeException.class)
  public void encodeToBuffer() {
    // if other than EncodeException happens here, then
    // there is probably a leak closing the netty buffer output stream
    codec.toBuffer(new RuntimeException("Unsupported"));
  }

  @Test
  public void testDefaultConstraints() {
    testReadConstraints(
      DEFAULT_MAX_DEPTH,
      DEFAULT_MAX_NUM_LEN,
      DEFAULT_MAX_STRING_LEN,
      DEFAULT_MAX_NAME_LEN);
  }

  public static void testReadConstraints(int defaultMaxDepth,
                                         int maxNumberLength,
                                         int defaultMaxStringLength,
                                         int defaultMaxNameLength) {
    testMaxNestingDepth(defaultMaxDepth);
    try {
      testMaxNestingDepth(defaultMaxDepth + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }
    testMaxNumberLength(maxNumberLength);
    try {
      testMaxNumberLength(maxNumberLength + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }

    testMaxStringLength(defaultMaxStringLength);
    try {
      testMaxStringLength(defaultMaxStringLength + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }

    testMaxNameLength(defaultMaxNameLength);
    try {
      testMaxNameLength(defaultMaxNameLength + 1);
      Assert.fail();
    } catch (DecodeException expected) {
    }
  }

  private static JsonArray testMaxNestingDepth(int depth) {
    String json = "[".repeat(depth) + "]".repeat(depth);
    return new JsonArray(json);
  }

  private static JsonObject testMaxNumberLength(int len) {
    String json = "{\"number\":" + "1".repeat(len) + "}";
    return new JsonObject(json);
  }

  private static JsonObject testMaxStringLength(int len) {
    String json = "{\"string\":\"" + "a".repeat(len) + "\"}";
    return new JsonObject(json);
  }

  private static JsonObject testMaxNameLength(int len) {
    String json = "{\"" + "a".repeat(len) + "\":3}";
    return new JsonObject(json);
  }
}
