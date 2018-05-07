/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Local maps can be used to share data safely in a single Vert.x instance.
 * <p>
 * By default the map allows immutable keys and values.
 * Custom keys and values should implement {@link Shareable} interface. The map returns their copies.
 * <p>
 * This ensures there is no shared access to mutable state from different threads (e.g. different event loops) in the
 * Vert.x instance, and means you don't have to protect access to that state using synchronization or locks.
 * <p>
 *
 * Since the version 3.4, this class extends the {@link Map} interface. However some methods are only accessible in Java.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @see Shareable
 */
@VertxGen
public interface LocalMap<K, V> extends Map<K, V> {

  /**
   * Get a value from the map
   *
   * @param key the key
   * @return the value, or null if none
   */
  V get(Object key);

  /**
   * Put an entry in the map
   *
   * @param key   the key
   * @param value the value
   * @return return the old value, or null if none
   */
  V put(K key, V value);

  /**
   * Remove an entry from the map
   *
   * @param key the key
   * @return the old value
   */
  V remove(Object key);

  /**
   * Clear all entries in the map
   */
  void clear();

  /**
   * Get the size of the map
   *
   * @return the number of entries in the map
   */
  int size();

  /**
   * @return true if there are zero entries in the map
   */
  boolean isEmpty();

  /**
   * Put the entry only if there is no existing entry for that key
   *
   * @param key   the key
   * @param value the value
   * @return the old value or null, if none
   */
  V putIfAbsent(K key, V value);

  /**
   * Remove the entry only if there is an entry with the specified key and value.
   * <p>
   * This method is the poyglot version of {@link #remove(Object, Object)}.
   *
   * @param key   the key
   * @param value the value
   * @return true if removed
   */
  boolean removeIfPresent(K key, V value);

  /**
   * Replace the entry only if there is an existing entry with the specified key and value.
   * <p>
   * This method is the polyglot version of {@link #replace(Object, Object, Object)}.
   *
   * @param key      the key
   * @param oldValue the old value
   * @param newValue the new value
   * @return true if removed
   */
  boolean replaceIfPresent(K key, V oldValue, V newValue);

  /**
   * Replace the entry only if there is an existing entry with the key
   *
   * @param key   the key
   * @param value the new value
   * @return the old value
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
   * @return the set of values in the map
   */
  @GenIgnore
  Collection<V> values();

  /**
   * Attempts to compute a mapping for the specified key and its current
   * mapped value (or {@code null} if there is no current mapping).
   * <p>
   * If the function returns {@code null}, the mapping is removed (or
   * remains absent if initially absent).  If the function itself throws an
   * (unchecked) exception, the exception is rethrown, and the current mapping
   * is left unchanged.
   *
   * @param key               key with which the specified value is to be associated
   * @param remappingFunction the function to compute a value
   * @return the new value associated with the specified key, or null if none
   **/
  @GenIgnore
  @Override
  V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

  /**
   * If the specified key is not already associated with a value (or is mapped
   * to {@code null}), attempts to compute its value using the given mapping
   * function and enters it into this map unless {@code null}.
   * <p>
   * If the function returns {@code null} no mapping is recorded. If
   * the function itself throws an (unchecked) exception, the
   * exception is rethrown, and no mapping is recorded.
   *
   * @param key             key with which the specified value is to be associated
   * @param mappingFunction the function to compute a value
   * @return the current (existing or computed) value associated with
   * the specified key, or null if the computed value is null
   */
  @GenIgnore
  @Override
  V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

  /**
   * If the value for the specified key is present and non-null, attempts to
   * compute a new mapping given the key and its current mapped value.
   * <p>
   * If the function returns {@code null}, the mapping is removed.  If the
   * function itself throws an (unchecked) exception, the exception is
   * rethrown, and the current mapping is left unchanged.
   *
   * @param key               key with which the specified value is to be associated
   * @param remappingFunction the function to compute a value
   * @return the new value associated with the specified key, or null if none
   */
  @GenIgnore
  @Override
  V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);


  /**
   * Returns {@code true} if this map contains a mapping for the specified
   * key.
   *
   * @param key key whose presence in this map is to be tested
   * @return {@code true} if this map contains a mapping for the specified key
   */
  @Override
  boolean containsKey(Object key);

  /**
   * Returns @{code true} if this map maps one or more keys to the
   * specified value.
   *
   * @param value value whose presence in this map is to be tested
   * @return @{code true} if this map maps one or more keys to the specified value
   */
  @Override
  boolean containsValue(Object value);

  /**
   * Returns a {@link Set} view of the mappings contained in this map.
   * <p>
   * Unlike the default {@link Map} implementation, the set is <strong>not</strong> backed by the map. So changes
   * made to the map are not reflected in the set. Entries added or remove to the set are not reflected to the map.
   * In addition, the entries are not modifiable ({@link java.util.Map.Entry#setValue(Object)} is not supported).
   *
   * @return a set view of the mappings contained in this map
   */
  @GenIgnore
  @Override
  Set<Entry<K, V>> entrySet();

  /**
   * Performs the given action for each entry in this map until all entries
   * have been processed or the action throws an exception.
   * <p>
   * Exceptions thrown by the action are relayed to the caller.
   *
   * @param action The action to be performed for each entry
   */
  @GenIgnore
  @Override
  void forEach(BiConsumer<? super K, ? super V> action);

  /**
   * Returns the value to which the specified key is mapped, or
   * {@code defaultValue} if this map contains no mapping for the key.
   *
   * @param key          the key whose associated value is to be returned
   * @param defaultValue the default mapping of the key
   * @return the value to which the specified key is mapped, or {@code defaultValue} if this map contains no mapping
   * for the key
   */
  @Override
  V getOrDefault(Object key, V defaultValue);

  /**
   * If the specified key is not already associated with a value or is
   * associated with null, associates it with the given non-null value.
   * Otherwise, replaces the associated value with the results of the given
   * remapping function, or removes if the result is {@code null}. This
   * method may be of use when combining multiple mapped values for a key.
   *
   * @param key               key with which the resulting value is to be associated
   * @param value             the non-null value to be merged with the existing value
   *                          associated with the key or, if no existing value or a null value
   *                          is associated with the key, to be associated with the key
   * @param remappingFunction the function to recompute a value if present
   * @return the new value associated with the specified key, or null if no
   * value is associated with the key
   */
  @GenIgnore
  @Override
  V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction);

  /**
   * Copies all of the mappings from the specified map to this map.  The effect of this call is equivalent to that
   * of calling {@link #put(Object, Object) put(k, v)} on this map once for each mapping from key {@code k} to value
   * {@code v} in the specified map.  The behavior of this operation is undefined if the specified map is modified
   * while the operation is in progress.
   *
   * @param m mappings to be stored in this map
   */
  @GenIgnore
  @Override
  void putAll(Map<? extends K, ? extends V> m);

  /**
   * Removes the entry for the specified key only if it is currently
   * mapped to the specified value.
   *
   * @param key   key with which the specified value is associated
   * @param value value expected to be associated with the specified key
   * @return {@code true} if the value was removed
   */
  @Override
  @GenIgnore
  boolean remove(Object key, Object value);

  /**
   * Replaces the entry for the specified key only if currently
   * mapped to the specified value.
   *
   * @param key      key with which the specified value is associated
   * @param oldValue value expected to be associated with the specified key
   * @param newValue value to be associated with the specified key
   * @return {@code true} if the value was replaced
   */
  @Override
  @GenIgnore
  boolean replace(K key, V oldValue, V newValue);

  /**
   * Replaces each entry's value with the result of invoking the given
   * function on that entry until all entries have been processed or the
   * function throws an exception.  Exceptions thrown by the function are
   * relayed to the caller.
   *
   * @param function the function to apply to each entry
   */
  @GenIgnore
  @Override
  void replaceAll(BiFunction<? super K, ? super V, ? extends V> function);
}
