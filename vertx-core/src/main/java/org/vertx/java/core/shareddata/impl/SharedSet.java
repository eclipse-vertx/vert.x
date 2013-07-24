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
