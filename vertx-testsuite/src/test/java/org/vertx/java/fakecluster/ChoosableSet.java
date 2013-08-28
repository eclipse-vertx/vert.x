/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package org.vertx.java.fakecluster;

import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.spi.cluster.ChoosableIterable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ChoosableSet<T> implements ChoosableIterable<T> {

  private final Set<T> ids;
  private volatile Iterator<T> iter;

  public ChoosableSet(int initialSize) {
    ids = new ConcurrentHashSet<>(initialSize);
  }

  public int size() {
    return ids.size();
  }

  public void add(T elem) {
    ids.add(elem);
  }

  public void remove(T elem) {
    ids.remove(elem);
  }

  public void merge(ChoosableSet<T> toMerge) {
    ids.addAll(toMerge.ids);
  }

  public boolean isEmpty() {
    return ids.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return ids.iterator();
  }

  public synchronized T choose() {
    if (!ids.isEmpty()) {
      if (iter == null || !iter.hasNext()) {
        iter = ids.iterator();
      }
      try {
        return iter.next();
      } catch (NoSuchElementException e) {
        return null;
      }
    } else {
      return null;
    }
  }
}
