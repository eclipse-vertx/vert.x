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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedSet<E> implements Set<E> {

  private final Map<E, Object> map = new SharedMap<>();

  private static final Object O = "wibble";

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  public Iterator<E> iterator() {
    return map.keySet().iterator();
  }

  public Object[] toArray() {
    return map.keySet().toArray();
  }

  public <T> T[] toArray(T[] ts) {
    return map.keySet().toArray(ts);
  }

  public boolean add(E e) {
    return map.put(e, O) == null;
  }

  public boolean remove(Object o) {
    return map.remove(o) != null;
  }

  public boolean containsAll(Collection<?> objects) {
    return map.keySet().containsAll(objects);
  }

  public boolean addAll(Collection<? extends E> es) {
    for (E e : es) {
      map.put(e, O);
    }
    return true;
  }

  public boolean retainAll(Collection<?> objects) {
    return false;
  }

  public boolean removeAll(Collection<?> objects) {
    boolean removed = false;
    for (Object obj: objects) {
      if (map.remove(obj) != null) {
        removed = true;
      }
    }
    return removed;
  }

  public void clear() {
    map.clear();
  }

  @Override
  public boolean equals(Object o) {
    return map.equals(o);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }
}
