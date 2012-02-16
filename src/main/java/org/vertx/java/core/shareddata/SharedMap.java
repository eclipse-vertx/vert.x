/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.shareddata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class SharedMap<K, V> implements ConcurrentMap<K, V> {

  private final ConcurrentMap<K, V> map = new ConcurrentHashMap<>();

  public V putIfAbsent(K k, V v) {
    SharedData.checkType(k);
    SharedData.checkType(v);
    return map.putIfAbsent(k, v);
  }

  public boolean remove(Object o, Object o1) {
    return map.remove(o, o1);
  }

  public boolean replace(K k, V v, V v1) {
    SharedData.checkType(v1);
    return map.replace(k, v, v1);
  }

  public V replace(K k, V v) {
    SharedData.checkType(v);
    V ret = map.replace(k, v);
    return SharedData.copyIfRequired(ret);
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsKey(Object o) {
    return map.containsKey(o);
  }

  public boolean containsValue(Object o) {
    return map.containsValue(o);
  }

  public V get(Object o) {
    return SharedData.copyIfRequired(map.get(o));
  }

  public V put(K k, V v) {
    SharedData.checkType(k);
    SharedData.checkType(v);
    return map.put(k, v);
  }

  public V remove(Object o) {
    return SharedData.copyIfRequired(map.remove(o));
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      SharedData.checkType(entry.getKey());
      SharedData.checkType(entry.getValue());
      this.map.put(entry.getKey(), entry.getValue());
    }
  }

  public void clear() {
    map.clear();
  }

  public Set<K> keySet() {
    Set<K> copied = new HashSet<>();
    for (K k: map.keySet()) {
      copied.add(SharedData.copyIfRequired(k));
    }
    return copied;
  }

  public Collection<V> values() {
    Collection<V> copied = new ArrayList<>();
    for (V v: map.values()) {
      copied.add(SharedData.copyIfRequired(v));
    }
    return copied;
  }

  public Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> entries = new HashSet<>();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      entries.add(new Entry<>(entry));
    }
    return entries;
  }

  @Override
  public boolean equals(Object o) {
    return map.equals(o);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  private static class Entry<K, V> implements Map.Entry<K, V> {

    final Map.Entry<K, V> internalEntry;

    Entry(Map.Entry<K, V> internalEntry) {
      this.internalEntry = internalEntry;
    }

    public K getKey() {
      return SharedData.copyIfRequired(internalEntry.getKey());
    }

    public V getValue() {
      return SharedData.copyIfRequired(internalEntry.getValue());
    }

    public V setValue(V value) {
      V old = internalEntry.getValue();
      SharedData.checkType(value);
      internalEntry.setValue(value);
      return old;
    }
  }
}
