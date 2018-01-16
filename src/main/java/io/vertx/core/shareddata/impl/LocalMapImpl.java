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

package io.vertx.core.shareddata.impl;

import io.vertx.core.shareddata.LocalMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.vertx.core.shareddata.impl.Checker.checkType;
import static io.vertx.core.shareddata.impl.Checker.copyIfRequired;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class LocalMapImpl<K, V> implements LocalMap<K, V> {

  private final ConcurrentMap<String, LocalMap<?, ?>> maps;
  private final String name;
  private final ConcurrentMap<K, V> map = new ConcurrentHashMap<>();

  LocalMapImpl(String name, ConcurrentMap<String, LocalMap<?, ?>> maps) {
    this.name = name;
    this.maps = maps;
  }

  @Override
  public V get(Object key) {
    return copyIfRequired(map.get(key));
  }

  @Override
  public V put(K key, V value) {
    checkType(key);
    checkType(value);
    return map.put(key, value);
  }

  @Override
  public V remove(Object key) {
    return copyIfRequired(map.remove(key));
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public V putIfAbsent(K key, V value) {
    checkType(key);
    checkType(value);
    return copyIfRequired(map.putIfAbsent(key, value));
  }

  @Override
  public boolean remove(Object key, Object value) {
    return map.remove(key, value);
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return map.replace(key, oldValue, newValue);
  }

  @Override
  public boolean removeIfPresent(K key, V value) {
    return map.remove(key, value);
  }

  @Override
  public boolean replaceIfPresent(K key, V oldValue, V newValue) {
    checkType(key);
    checkType(oldValue);
    checkType(newValue);
    return map.replace(key, oldValue, newValue);
  }

  @Override
  public V replace(K key, V value) {
    checkType(key);
    checkType(value);
    return copyIfRequired(map.replace(key, value));
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    map.replaceAll((k, v) -> {
      checkType(k);
      checkType(v);
      V output = function.apply(k, v);
      if (output != null) {
        checkType(output);
      }
      return output;
    });
  }

  @Override
  public void close() {
    maps.remove(name);
  }

  @Override
  public Set<K> keySet() {
    Set<K> keys = new HashSet<>(map.size());
    for (K k : map.keySet()) {
      keys.add(copyIfRequired(k));
    }
    return keys;
  }

  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>(map.size());
    for (V v : map.values()) {
      values.add(copyIfRequired(v));
    }
    return values;
  }

  /**
   * Composes the given bi-function ({@code f(a,b)}) with a function checking the type of the output:
   * {@code checkType(f(a,b))}. So the output of the given function is checked to verify that it uses a valid type.
   *
   * @param function the function
   * @return the composition
   */
  private BiFunction<? super K, ? super V, ? extends V> typeChecked(BiFunction<? super K, ? super V, ? extends V>
                                                                        function) {
    return (k, v) -> {
      checkType(k);
      V output = function.apply(k, v);
      if (output != null) {
        checkType(output);
      }
      return output;
    };
  }

  /**
   * Composes the given function ({@code f(a)}) with a function checking the type of the output. So the output of the
   * given function is checked to verify that is uses a valid type.
   *
   * @param function the function
   * @return the composition
   */
  private Function<? super K, ? extends V> typeChecked(Function<? super K, ? extends V>
                                                           function) {
    return k -> {
      checkType(k);
      V output = function.apply(k);
      if (output != null) {
        checkType(output);
      }
      return output;
    };
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return map.compute(key, typeChecked(remappingFunction));
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return map.computeIfAbsent(key, typeChecked(mappingFunction));
  }

  @Override
  public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return map.computeIfPresent(key, typeChecked(remappingFunction));
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> entries = new HashSet<>(map.size());
    for (Map.Entry<K, V> entry : map.entrySet()) {
      entries.add(new Map.Entry<K, V>() {

        @Override
        public K getKey() {
          return copyIfRequired(entry.getKey());
        }

        @Override
        public V getValue() {
          return copyIfRequired(entry.getValue());
        }

        @Override
        public V setValue(V value) {
          throw new UnsupportedOperationException();
        }
      });
    }
    return entries;
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    // Cannot delegate, it needs to copy the objects to avoid modifications
    for (Map.Entry<K, V> entry : entrySet()) {
      action.accept(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return copyIfRequired(map.getOrDefault(key, defaultValue));
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    checkType(key);
    checkType(value);
    return map.merge(key, value, (k, v) -> {
      // No need to check the key, already check above.
      V output = remappingFunction.apply(k, v);
      if (output != null) {
        checkType(output);
      }
      return output;
    });
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    // Iterate over the set to entry and call `put` on each entry to validate the types
    for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public String toString() {
    return map.toString();
  }
}
