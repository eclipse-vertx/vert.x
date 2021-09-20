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

import java.util.BitSet;
import java.util.Random;
import java.util.Iterator;

/**
 * A simple Bloom Filter
 */
public class BloomFilter {

  private final BitSet hashes;
  private final RandomInRange prng;
  private static final double LN2 = 0.6931471805599453; // ln(2)

  /**
   * Create a new bloom filter.
   *
   * @param n Expected number of elements
   * @param m Desired size of the container in bits
   **/
  public BloomFilter(int n, int m) {
    // Number of hash functions
    int k = (int) Math.round(LN2 * m / n);
    if (k <= 0) k = 1;
    this.hashes = new BitSet(m);
    this.prng = new RandomInRange(m, k);
  }

  public void add(Object o) {
    prng.init(o);
    for (RandomInRange r : prng) {
      hashes.set(r.value);
    }
  }

  /**
   * If the element is in the container, returns true.
   * If the element is not in the container, returns true with a probability ≈ e^(-ln(2)² * m/n), otherwise false.
   * So, when m is large enough, the return value can be interpreted as:
   * - true  : the element is probably in the container
   * - false : the element is definitely not in the container
   **/
  public boolean contains(Object o) {
    prng.init(o);
    for (RandomInRange r : prng){
      if (!hashes.get(r.value)) {
        return false;
      }
    }
    return true;
  }

  public void clear() {
    hashes.clear();
  }

  private static class RandomInRange implements Iterable<RandomInRange>, Iterator<RandomInRange> {

    private final Random prng;
    private final int max; // Maximum value returned + 1
    private final int count; // Number of random elements to generate
    private int i = 0; // Number of elements generated
    public int value; // The current value

    RandomInRange(int maximum, int k) {
      max = maximum;
      count = k;
      prng = new Random();
    }

    public void init(Object o) {
      prng.setSeed(o.hashCode());
    }

    @Override
    public Iterator<RandomInRange> iterator() {
      i = 0;
      return this;
    }

    @Override
    public RandomInRange next() {
      i++;
      value = prng.nextInt() % max;
      if (value < 0) {
        value = -value;
      }
      return this;
    }

    @Override
    public boolean hasNext() {
      return i < count;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
