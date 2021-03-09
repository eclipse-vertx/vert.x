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

package io.vertx.core.http.headers;

import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CaseInsensitiveHeadersTest extends VertxHttpHeadersTest {

  public CaseInsensitiveHeadersTest() {
    sameHash1 = "AZ";
    sameHash2 = "\u0080Y";
    sameBucket1 = "A";
    sameBucket2 = "R";
  }

  protected HeadersMultiMap newMultiMap() {
    return new HeadersMultiMap();
  }

  @Test
  public void checkNameCollision() {
    assertEquals(hash(sameHash1), hash(sameHash2));
    assertNotEquals(hash(sameBucket1), hash(sameBucket2));
    assertEquals(index(hash(sameBucket1)), index(hash(sameBucket2)));
  }

  // hash function copied from method under test
  private static int hash(String name) {
    int h = 0;
    for (int i = name.length() - 1; i >= 0; i--) {
      char c = name.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        c += 32;
      }
      h = 31 * h + c;
    }

    if (h > 0) {
      return h;
    } else if (h == Integer.MIN_VALUE) {
      return Integer.MAX_VALUE;
    } else {
      return -h;
    }
  }

  private static int index(int hash) {
    return hash % 17;
  }

  // construct a string with hash==MIN_VALUE
  // to get coverage of the if in hash()
  // we will calculate the representation of
  // MAX_VALUE+1 in base31, which wraps around to
  // MIN_VALUE in int representation
  @Test
  public void testHashMININT() {
    MultiMap mm = newMultiMap();
    String name1 = "";
    long value = Integer.MAX_VALUE;
    value++;
    int base = 31;
    long pow = 1;

    while (value > pow * base) {
      pow *= base;
    }

    while (pow != 0) {
      long mul = value / pow;
      name1 = ((char) mul) + name1;
      value -= pow * mul;
      pow /= base;
    }
    name1 = ((char) value) + name1;
    mm.add(name1, "value");
    assertEquals("value", mm.get(name1));
  }
}
