/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.shareddata.impl;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.shareddata.ConcurrentSharedMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedMap<K, V> implements ConcurrentSharedMap<K, V> {

  private static final Logger log = LoggerFactory.getLogger(SharedMap.class);

  private final ConcurrentMap<K, V> map = new ConcurrentHashMap<>();

  public V putIfAbsent(K k, V v) {
    Checker.checkType(k);
    Checker.checkType(v);
    return map.putIfAbsent(k, v);
  }

  public boolean remove(Object o, Object o1) {
    return map.remove(o, o1);
  }

  public boolean replace(K k, V v, V v1) {
    Checker.checkType(v1);
    return map.replace(k, v, v1);
  }

  public V replace(K k, V v) {
    Checker.checkType(v);
    V ret = map.replace(k, v);
    return Checker.copyIfRequired(ret);
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
    return Checker.copyIfRequired(map.get(o));
  }

  public V put(K k, V v) {
    Checker.checkType(k);
    Checker.checkType(v);
    return map.put(k, v);
  }

  public V remove(Object o) {
    return Checker.copyIfRequired(map.remove(o));
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      Checker.checkType(entry.getKey());
      Checker.checkType(entry.getValue());
      this.map.put(entry.getKey(), entry.getValue());
    }
  }

  public void clear() {
    map.clear();
  }

  public Set<K> keySet() {
    Set<K> copied = new HashSet<>();
    for (K k: map.keySet()) {
      copied.add(Checker.copyIfRequired(k));
    }
    return copied;
  }

  public Collection<V> values() {
    Collection<V> copied = new ArrayList<>();
    for (V v: map.values()) {
      copied.add(Checker.copyIfRequired(v));
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
      return Checker.copyIfRequired(internalEntry.getKey());
    }

    public V getValue() {
      return Checker.copyIfRequired(internalEntry.getValue());
    }

    public V setValue(V value) {
      V old = internalEntry.getValue();
      Checker.checkType(value);
      internalEntry.setValue(value);
      return old;
    }
  }
}
