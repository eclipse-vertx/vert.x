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
package io.vertx.core.file.impl;

import org.junit.Test;

import static org.junit.Assert.*;

public class InvertedBloomFilterTest {

  @Test
  public void testTheBasics() {
    InvertedBloomFilter<String>  f = new InvertedBloomFilter<>(2);
    String a = "a.txt";
    String b = "b.txt";
    String c = "c.txt";

    shouldNotContain("nothing should be contained at all", f, a);

    f.add(a);
    shouldContain("now it should", f, a);

    shouldNotContain("false unless the hash collides", f, b);
    f.add(b);

    shouldContain("original should still return true", f, a);
    shouldContain("new array should still return true", f, b);

    // Handling collisions. "a.txt" and "b.txt" hash to the same
    // index using the current hash function.
    shouldNotContain("colliding array returns false", f, c);

    f.add(c);

    shouldContain("colliding array returns true in second call", f, c);
    shouldNotContain("original colliding array returns false", f, a);

    // re add a.txt
    f.add(a);

    shouldContain("original colliding array returns true", f, b);
    shouldNotContain("colliding array returns false", f, c);
  }

  @Test
  public void testSizeRounding() {
    assertEquals(4, new InvertedBloomFilter<>(3).size());
    assertEquals(4, new InvertedBloomFilter<>(4).size());
    assertEquals(256, new InvertedBloomFilter<>(129).size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTooLargeSize() {
    int size = (1 << 30) + 1;
    new InvertedBloomFilter<>(size);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTooSmallSize() {
    new InvertedBloomFilter<>(0);
  }

  private <T> void shouldContain(String msg, InvertedBloomFilter<T> f, T ref) {
    boolean test = f.test(ref);
    assertTrue(String.format("should contain, %s: id %s", msg, ref), test);
  }

  private <T> void shouldNotContain(String msg, InvertedBloomFilter<T> f, T ref) {
    boolean test = f.test(ref);
    assertFalse(String.format("should not contain, %s: id %s", msg, ref), test);
  }
}
