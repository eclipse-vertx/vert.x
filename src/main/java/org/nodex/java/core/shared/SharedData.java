/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.shared;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>Sometimes it is desirable to share immutable data between different event loops, for example to implement a
 * cache of data.</p>
 * <p>This class allows instances of shared data structures to be looked up and used from different event loops.</p>
 * <p>The data structures themselves will only allow certain data types to be stored into them. This shields the
 * user
 * from worrying about any thread safety issues might occur if mutable objects were shared between event loops.</p>
 * <p>The following types can be stored in a shared data structure:</p>
 * <pre>
 *   {@link String}
 *   {@link Integer}
 *   {@link Long}
 *   {@link Double}
 *   {@link Float}
 *   {@link Short}
 *   {@link Byte}
 *   {@link Character}
 *   {@link java.math.BigDecimal}
 *   {@link byte[]} - this will be automatically copied, and the copy will be stored in the structure.
 *   {@link org.nodex.java.core.buffer.Buffer} - this will be automatically copied, and the copy will be stored in the
 *   structure.
 *   {@link org.nodex.java.core.Immutable} - if you mark your own class as {@code Immutable} you will be able to
 *   store it in a shared data structure. Use this at your own risk. You need to make sure your class really is
 *   immutable before you mark it.
 * </pre>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedData {

  private static ConcurrentMap<Object, SharedMap<?, ?>> maps = new NonBlockingHashMap<>();
  private static ConcurrentMap<Object, SharedSet<?>> sets = new NonBlockingHashMap<>();
  private static ConcurrentMap<Object, SharedCounter> counters = new NonBlockingHashMap<>();
  private static ConcurrentMap<Object, SharedQueue> queues = new NonBlockingHashMap<>();

  /**
   * Return a {@code Map} with the specific {@code name}. All invocations of this method with the same value of {@code name}
   * are guaranteed to return the same {@code Map} instance. <p>
   * The Map instance returned is a lock free Map which supports a very high degree of concurrency.
   */
  public static <K, V> Map<K, V> getMap(Object name) {
    SharedMap<K, V> map = (SharedMap<K, V>) maps.get(name);
    if (map == null) {
      map = new SharedMap<>();
      SharedMap prev = maps.putIfAbsent(name, map);
      if (prev != null) {
        map = prev;
      }
    }
    return map;
  }

  /**
   * Return a {@code Set} with the specific {@code name}. All invocations of this method with the same value of {@code name}
   * are guaranteed to return the same {@code Set} instance. <p>
   * The Set instance returned is a lock free Map which supports a very high degree of concurrency.
   */
  public static <E> Set<E> getSet(Object name) {
    SharedSet<E> set = (SharedSet<E>) sets.get(name);
    if (set == null) {
      set = new SharedSet<>();
      SharedSet prev = sets.putIfAbsent(name, set);
      if (prev != null) {
        set = prev;
      }
    }
    return set;
  }

//  public static SharedCounter getCounter(Object name) {
//    SharedCounter counter = counters.get(name);
//    if (counter == null) {
//      counter = new SharedCounter();
//      SharedCounter prev = counters.putIfAbsent(name, counter);
//      if (prev != null) {
//        counter = prev;
//      }
//    }
//    return counter;
//  }

//  public static <E> SharedQueue<E> getQueue(Object name) {
//    SharedQueue<E> queue = (SharedQueue<E>) queues.get(name);
//    if (queue == null) {
//      queue = new SharedQueue<>();
//      SharedQueue prev = queues.putIfAbsent(name, queue);
//      if (prev != null) {
//        queue = prev;
//      }
//    }
//    return queue;
//  }

  /**
   * Remove the {@code Map} with the specifiec {@code name}.
   */
  public static boolean removeMap(Object name) {
    return maps.remove(name) != null;
  }

  /**
   * Remove the {@code Set} with the specifiec {@code name}.
   */
  public static boolean removeSet(Object name) {
    return sets.remove(name) != null;
  }

//  public static boolean removeCounter(Object name) {
//    return counters.remove(name) != null;
//  }
//
//  public static boolean removeQueue(Object name) {
//    return queues.remove(name) != null;
//  }
}
