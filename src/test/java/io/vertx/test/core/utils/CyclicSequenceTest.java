/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.utils;

import io.vertx.core.impl.utils.CyclicSequence;
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

public class CyclicSequenceTest {

  @Test
  public void testAdd() {
    CyclicSequence<String> seq = new CyclicSequence<String>().add("s1");
    assertEquals(Collections.singletonList("s1"), toList(seq));
    assertEquals(Arrays.asList("s1", "s2"), toList(seq.add("s2")));
    assertEquals(Collections.singletonList("s1"), toList(seq));
  }

  @Test
  public void testRemove() {
    CyclicSequence<String> seq = new CyclicSequence<String>().add("s1").add("s2").add("s1").add("s2");
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
    CyclicSequence<String> seq = new CyclicSequence<String>().add("s1").add(null).add("s2");
    assertEquals(Arrays.asList("s1", null, "s2"), toList(seq));
    assertEquals(Arrays.asList("s1", "s2"), toList(seq.remove(null)));
  }

  @Test
  public void testRoundRobin() throws Exception {
    int iter = 1_000_000;
    int range = 10;
    CyclicSequence<AtomicInteger> tmp = new CyclicSequence<>();
    for (int i = 0; i < range; i++) {
      tmp = tmp.add(new AtomicInteger());
    }
    CyclicSequence<AtomicInteger> handlers = tmp;
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

  private static <T> List<T> toList(CyclicSequence<T> seq) {
    ArrayList<T> ret = new ArrayList<>();
    for (T elt : seq) {
      ret.add(elt);
    }
    return ret;
  }

}
