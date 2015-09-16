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
