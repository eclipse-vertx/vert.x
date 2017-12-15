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

package io.vertx.test.fakecluster;

import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.spi.cluster.ChoosableIterable;

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

  public boolean remove(T elem) {
    return ids.remove(elem);
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
