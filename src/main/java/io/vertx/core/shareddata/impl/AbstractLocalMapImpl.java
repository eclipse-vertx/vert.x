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

package io.vertx.core.shareddata.impl;

import static io.vertx.core.shareddata.impl.Checker.checkType;
import static io.vertx.core.shareddata.impl.Checker.copyIfRequired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.vertx.core.shareddata.LocalMap;

/**
 * Encapsulates methods used commonly among {@link LocalMap} implementations.
 * 
 * @param <S> points to self, required for dealing with the
 *            {@link #getAllLocalMaps() map of local maps}
 */
abstract class AbstractLocalMapImpl<K, V, S extends LocalMap<?, ?>> implements LocalMap<K, V> {

  abstract String getName();

  abstract ConcurrentMap<K, V> getInternalMap();

  abstract ConcurrentMap<String, S> getAllLocalMaps();

  @Override
  public V get(Object key) {
    return copyIfRequired(getInternalMap().get(key));
  }

  @Override
  public V put(K key, V value) {
    checkType(key);
    checkType(value);
    return getInternalMap().put(key, value);
  }

  @Override
  public V remove(Object key) {
    return copyIfRequired(getInternalMap().remove(key));
  }

  @Override
  public void clear() {
    getInternalMap().clear();
  }

  @Override
  public int size() {
    return getInternalMap().size();
  }

  @Override
  public boolean isEmpty() {
    return getInternalMap().isEmpty();
  }

  @Override
  public V putIfAbsent(K key, V value) {
    checkType(key);
    checkType(value);
    return copyIfRequired(getInternalMap().putIfAbsent(key, value));
  }

  @Override
  public boolean remove(Object key, Object value) {
    return getInternalMap().remove(key, value);
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return getInternalMap().replace(key, oldValue, newValue);
  }

  @Override
  public boolean removeIfPresent(K key, V value) {
    return remove(key, value);
  }

  @Override
  public boolean replaceIfPresent(K key, V oldValue, V newValue) {
    checkType(key);
    checkType(oldValue);
    checkType(newValue);
    return getInternalMap().replace(key, oldValue, newValue);
  }

  @Override
  public V replace(K key, V value) {
    checkType(key);
    checkType(value);
    return copyIfRequired(getInternalMap().replace(key, value));
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> f) {
    getInternalMap().replaceAll((k, v) -> {
      checkType(k);
      checkType(v);
      V output = f.apply(k, v);
      if (output != null) {
        checkType(output);
      }
      return output;
    });
  }

  @Override
  public void close() {
    getAllLocalMaps().remove(getName());
  }

  @Override
  public Set<K> keySet() {
    Set<K> keys = new HashSet<>(getInternalMap().size());
    for (K k : getInternalMap().keySet()) {
      keys.add(copyIfRequired(k));
    }
    return keys;
  }

  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>(getInternalMap().size());
    for (V v : getInternalMap().values()) {
      values.add(copyIfRequired(v));
    }
    return values;
  }

  /**
   * Composes the given bi-function ({@code f(a,b)}) with a function checking the
   * type of the output:
   * {@code checkType(f(a,b))}. So the output of the given function is checked to
   * verify that it uses a valid type.
   *
   * @param function the function
   * @return the composition
   */
  private BiFunction<? super K, ? super V, ? extends V> typeChecked(
      BiFunction<? super K, ? super V, ? extends V> function) {
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
   * Composes the given function ({@code f(a)}) with a function checking the type
   * of the output. So the output of the
   * given function is checked to verify that is uses a valid type.
   *
   * @param function the function
   * @return the composition
   */
  private Function<? super K, ? extends V> typeChecked(Function<? super K, ? extends V> function) {
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
    return getInternalMap().compute(key, typeChecked(remappingFunction));
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return getInternalMap().computeIfAbsent(key, typeChecked(mappingFunction));
  }

  @Override
  public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return getInternalMap().computeIfPresent(key, typeChecked(remappingFunction));
  }

  @Override
  public boolean containsKey(Object key) {
    return getInternalMap().containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return getInternalMap().containsValue(value);
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> entries = new HashSet<>(getInternalMap().size());
    for (Map.Entry<K, V> entry : getInternalMap().entrySet()) {
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
    return copyIfRequired(getInternalMap().getOrDefault(key, defaultValue));
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    checkType(key);
    checkType(value);
    return getInternalMap().merge(key, value, (k, v) -> {
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
    // Iterate over the set to entry and call `put` on each entry to validate the
    // types
    for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public String toString() {
    return getInternalMap().toString();
  }

}
