/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExpectationTest {

  @Test
  public void testAnd() {
    Throwable f1 = new Throwable();
    Expectation<String> isNotA = new Expectation<String>() {
      @Override
      public boolean test(String value) {
        return !"a".equals(value);
      }
      @Override
      public Throwable describe(String value) {
        return f1;
      }
    };
    Throwable f2 = new Throwable();
    Expectation<String> isNotB = new Expectation<String>() {
      @Override
      public boolean test(String value) {
        return !"b".equals(value);
      }
      @Override
      public Throwable describe(String value) {
        return f2;
      }
    };
    Expectation<String> isNotANorIsB = isNotA.and(isNotB);
    assertFalse(isNotA.test("a"));
    assertTrue(isNotA.test("b"));
    assertTrue(isNotA.test("c"));
    assertTrue(isNotB.test("a"));
    assertFalse(isNotB.test("b"));
    assertTrue(isNotB.test("c"));
    assertTrue(isNotANorIsB.test("c"));
    assertNull(isNotANorIsB.describe("c"));
    assertFalse(isNotANorIsB.test("a"));
    assertSame(f1, isNotANorIsB.describe("a"));
    assertFalse(isNotANorIsB.test("b"));
    assertSame(f2, isNotANorIsB.describe("b"));
  }

  @Test
  public void testOr() {
    Throwable f1 = new Throwable();
    Expectation<String> isA = new Expectation<String>() {
      @Override
      public boolean test(String value) {
        return "a".equals(value);
      }
      @Override
      public Throwable describe(String value) {
        return f1;
      }
    };
    Throwable f2 = new Throwable();
    Expectation<String> isB = new Expectation<String>() {
      @Override
      public boolean test(String value) {
        return "b".equals(value);
      }
      @Override
      public Throwable describe(String value) {
        return f2;
      }
    };
    Expectation<String> isAorIsB = isA.or(isB);
    assertTrue(isA.test("a"));
    assertFalse(isA.test("b"));
    assertFalse(isA.test("c"));
    assertFalse(isB.test("a"));
    assertTrue(isB.test("b"));
    assertFalse(isB.test("c"));
    assertFalse(isAorIsB.test("c"));
    assertSame(f1, isAorIsB.describe("c"));
    assertTrue(isAorIsB.test("a"));
    assertNull(isAorIsB.describe("a"));
    assertTrue(isAorIsB.test("b"));
    assertNull(isAorIsB.describe("b"));
  }
}
