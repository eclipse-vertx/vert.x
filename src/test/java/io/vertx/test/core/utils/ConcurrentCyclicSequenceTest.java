/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.utils;

import io.vertx.core.impl.utils.ConcurrentCyclicSequence;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConcurrentCyclicSequenceTest {

  @Test
  public void testEmpty() {
    ConcurrentCyclicSequence<String> empty = new ConcurrentCyclicSequence<>();
    for (int j = 0;j < 1;j++) {
      for (int i = 0;i < 3;i++) {
        assertEquals(0, empty.size());
        assertEquals(0, empty.index());
        assertEquals(null, empty.first());
        empty.next();
      }
      empty = empty.remove("does-not-exist");
    }
  }

  @Test
  public void testAdd() {
    ConcurrentCyclicSequence<String> seq = new ConcurrentCyclicSequence<String>().add("s1");
    assertEquals(Collections.singletonList("s1"), toList(seq));
    assertEquals(Arrays.asList("s1", "s2"), toList(seq.add("s2")));
    assertEquals(Collections.singletonList("s1"), toList(seq));
  }

  @Test
  public void testRemove() {
    ConcurrentCyclicSequence<String> seq = new ConcurrentCyclicSequence<String>().add("s1").add("s2").add("s1").add("s2");
    assertEquals(Arrays.asList("s1", "s2", "s1", "s2"), toList(seq));
    assertEquals(Arrays.asList("s1", "s1", "s2"), toList(seq.remove("s2")));
    assertEquals(Arrays.asList("s1", "s1"), toList(seq.remove("s2").remove("s2")));
    assertEquals(Arrays.asList("s2", "s1", "s2"), toList(seq.remove("s1")));
    assertEquals(Arrays.asList("s2", "s2"), toList(seq.remove("s1").remove("s1")));
    assertEquals(Arrays.asList("s1", "s2"), toList(seq.remove("s1").remove("s2")));
    assertEquals(Collections.emptyList(), toList(seq.remove("s1").remove("s2").remove("s1").remove("s2")));
    assertEquals(Arrays.asList("s1", "s2", "s1", "s2"), toList(seq));
  }

  @Test
  public void testNullElement() {
    ConcurrentCyclicSequence<String> seq = new ConcurrentCyclicSequence<>("s1", null, "s2", null);
    assertEquals(Arrays.asList("s1", null, "s2", null), toList(seq));
    assertEquals(Arrays.asList("s1", "s2", null), toList(seq.remove(null)));
  }

  @Test
  public void testRoundRobin() throws Exception {
    int iter = 1_000_000;
    int range = 10;
    ConcurrentCyclicSequence<AtomicInteger> tmp = new ConcurrentCyclicSequence<>();
    for (int i = 0; i < range; i++) {
      tmp = tmp.add(new AtomicInteger());
    }
    ConcurrentCyclicSequence<AtomicInteger> handlers = tmp;
    AtomicBoolean failed = new AtomicBoolean();
    int numThreads = 10;
    Thread[] threads = new Thread[numThreads];
    for (int i = 0;i < numThreads;i++) {
      threads[i] = new Thread(() -> {
        try {
          for (int j = 0;j < iter;j++) {
            handlers.next().incrementAndGet();
          }
        } catch (Exception e) {
          e.printStackTrace();
          failed.set(true);
        }
      });
    }
    for (Thread thread : threads) {
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }
    for (AtomicInteger i : handlers) {
      assertEquals(iter, i.get());
    }
    assertFalse(failed.get());
    int pos = handlers.index();
    assertTrue("Incorrect pos value " + pos, pos <= range);
  }

  private static <T> List<T> toList(ConcurrentCyclicSequence<T> seq) {
    ArrayList<T> ret = new ArrayList<>();
    for (T elt : seq) {
      ret.add(elt);
    }
    return ret;
  }
}
