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

import java.util.Comparator;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import io.vertx.core.shareddata.LocalSortedMap;

@SuppressWarnings("unchecked")
public class LocalSortedMapImpl<K extends Comparable<K>, V>
    extends AbstractLocalMapImpl<K, V, LocalSortedMap<?, ?>>
    implements LocalSortedMap<K, V> {

  private final String name;
  private final ConcurrentNavigableMap<K, V> map;
  private final ConcurrentMap<String, LocalSortedMap<?, ?>> maps;

  LocalSortedMapImpl(String name, ConcurrentMap<String, LocalSortedMap<?, ?>> maps) {
    this(name, new ConcurrentSkipListMap<>(), maps);
  }

  private LocalSortedMapImpl(
      String name,
      ConcurrentNavigableMap<K, V> map,
      ConcurrentMap<String, LocalSortedMap<?, ?>> maps) {
    this.name = name;
    this.map = map;
    this.maps = maps;
  }

  @Override
  String getName() {
    return name;
  }

  @Override
  ConcurrentMap<K, V> getInternalMap() {
    return map;
  }

  @Override
  ConcurrentMap<String, LocalSortedMap<?, ?>> getAllLocalMaps() {
    return maps;
  }

  @Override
  public Comparator<? super K> comparator() {
    return ((SortedMap<K, V>) map).comparator();
  }

  @Override
  public LocalSortedMapImpl<K, V> subMap(K fromKey, K toKey) {
    ConcurrentNavigableMap<K, V> sortedMap = (ConcurrentSkipListMap<K, V>) map;
    ConcurrentNavigableMap<K, V> subMap = sortedMap.subMap(fromKey, toKey);
    String name = this.name + "_" + fromKey.hashCode() + "-" + toKey.hashCode();

    return (LocalSortedMapImpl<K, V>) this.maps
        .computeIfAbsent(name, _ignored -> new LocalSortedMapImpl<>(name, subMap, maps));
  }

  @Override
  public LocalSortedMapImpl<K, V> headMap(K toKey) {
    return subMap(this.firstKey(), toKey);
  }

  @Override
  public LocalSortedMapImpl<K, V> tailMap(K fromKey) {
    return subMap(fromKey, this.lastKey());
  }

  @Override
  public K firstKey() {
    return ((SortedMap<K, V>) this.map).firstKey();
  }

  @Override
  public K lastKey() {
    return ((SortedMap<K, V>) this.map).lastKey();
  }

}
