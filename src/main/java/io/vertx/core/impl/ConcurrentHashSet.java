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

package io.vertx.core.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConcurrentHashSet<E> implements Set<E> {

  private final Map<E, Object> map;

  private static final Object OBJ = new Object();

  public ConcurrentHashSet(int size) {
    map = new ConcurrentHashMap<>(size);
  }

  public ConcurrentHashSet() {
    map = new ConcurrentHashMap<>();
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
  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  @Override
  public Iterator<E> iterator() {
    return map.keySet().iterator();
  }

  @Override
  public Object[] toArray() {
    return map.keySet().toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return map.keySet().toArray(a);
  }

  @Override
  public boolean add(E e) {
    return map.put(e, OBJ) == null;
  }

  @Override
  public boolean remove(Object o) {
    return map.remove(o) != null;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return map.keySet().containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean changed = false;
    for (E e: c) {
      if (map.put(e, OBJ) == null) {
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    map.clear();
  }
}
