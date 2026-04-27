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
package io.vertx.tests.json.jackson;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;

public class JacksonReadConstraintsTestBase {

  public static void testReadConstraints(int defaultMaxDepth,
                                         int maxNumberLength,
                                         int defaultMaxStringLength,
                                         int defaultMaxNameLength,
                                         long defaultMaxDocumentLength) {
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

    if (defaultMaxDocumentLength >= 0) {
      testMaxDocumentLength(defaultMaxDocumentLength);
      try {
        testMaxDocumentLength(defaultMaxDocumentLength + 1);
        Assert.fail();
      } catch (DecodeException expected) {
      }
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

  private static JsonArray testMaxDocumentLength(long len) {
    String prefix = len % 2 == 0 ? "[ " : "[";
    int num = (int) ((len - prefix.length()) / 2);
    StringBuilder sb = new StringBuilder((int) len);
    sb.append(prefix);
    for (int i = 0; i < num;i++) {
      sb.append("0,");
    }
    sb.setCharAt((int) (len - 1), ']');
    String json = sb.toString();
    return new JsonArray(json);
  }

}
