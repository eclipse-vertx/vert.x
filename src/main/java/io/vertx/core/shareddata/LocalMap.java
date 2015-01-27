/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.Collection;
import java.util.Set;

/**
 * Local maps can be used to share data safely in a single Vert.x instance.
 * <p>
 * The map only allows immutable keys and values in the map, OR certain mutable objects such as {@link io.vertx.core.buffer.Buffer}
 * instances which will be copied when they are added to the map.
 * <p>
 * This ensures there is no shared access to mutable state from different threads (e.g. different event loops) in the
 * Vert.x instance, and means you don't have to protect access to that state using synchronization or locks.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface LocalMap<K, V> {

  /**
   * Get a value from the map
   *
   * @param key  the key
   * @return  the value, or null if none
   */
  V get(K key);

  /**
   * Put an entry in the map
   *
   * @param key  the key
   * @param value  the value
   * @return  return the old value, or null if none
   */
  V put(K key, V value);

  /**
   * Remove an entry from the map
   *
   * @param key  the key
   * @return  the old value
   */
  V remove(K key);

  /**
   * Clear all entries in the map
   */
  void clear();

  /**
   * Get the size of the map
   *
   * @return  the number of entries in the map
   */
  int size();

  /**
   * @return true if there are zero entries in the map
   */
  boolean isEmpty();

  /**
   * Put the entry only if there is no existing entry for that key
   *
   * @param key  the key
   * @param value  the value
   * @return  the old value or null, if none
   */
  V putIfAbsent(K key, V value);

  /**
   * Remove the entry only if there is an entry with the specified key and value
   *
   * @param key  the key
   * @param value  the value
   * @return true if removed
   */
  boolean removeIfPresent(K key, V value);

  /**
   * Replace the entry only if there is an existing entry with the specified key and value
   *
   * @param key  the key
   * @param oldValue  the old value
   * @param newValue  the new value
   * @return true if removed
   */
  boolean replaceIfPresent(K key, V oldValue, V newValue);

  /**
   * Replace the entry only if there is an existing entry with the key
   *
   * @param key  the key
   * @param value  the new value
   * @return  the old value
   */
  V replace(K key, V value);

  /**
   * Close and release the map
   */
  void close();

  /**
   * @return the set of keys in the map
   */
  @GenIgnore
  Set<K> keySet();

  /**
   * @return  the set of values in the map
   */
  @GenIgnore
  Collection<V> values();

}
