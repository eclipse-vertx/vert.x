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

package io.vertx.core.impl.utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread safe cyclic sequence of elements that can be used for round robin.
 * <p/>
 * A sequence is immutable and mutations uses {@link #add(Object)} and {@link #remove(Object)}
 * to return a modified copy of the current instance.
 * <p/>
 * The iterator uses a volatile index, so it can be incremented concurrently by several
 * threads with locking.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CyclicSequence<T> implements Iterable<T>, Iterator<T> {

  private final AtomicInteger pos;
  private final Object[] elements;

  /**
   * Create a new empty sequence.
   */
  public CyclicSequence() {
    this(0, new Object[0]);
  }

  private CyclicSequence(int pos, Object[] elements) {
    this.pos = new AtomicInteger(elements.length > 0 ? pos % elements.length : 0);
    this.elements = elements;
  }

  /**
   * @return the current index
   */
  public int index() {
    return pos.get();
  }

  /**
   * @return the first element
   */
  @SuppressWarnings("unchecked")
  public T first() {
    return (T) (elements.length > 0 ? elements[0] : null);
  }

  /**
   * Copy the current sequence, add {@code element} at the tail of this sequence and returns it.
   * @param element the element to add
   * @return the result
   */
  public CyclicSequence<T> add(T element) {
    int len = elements.length;
    Object[] copy = Arrays.copyOf(elements, len + 1);
    copy[len] = element;
    return new CyclicSequence<>(pos.get(), copy);
  }

  /**
   * Remove the first occurrence of {@code element} in this sequence and returns it.
   * <p/>
   * If the sequence does not contains {@code element}, this instance is returned instead.
   *
   * @param element the element to remove
   * @return the result
   */
  public CyclicSequence<T> remove(T element) {
    int len = elements.length;
    for (int i = 0;i < len;i++) {
      if (Objects.equals(element, elements[i])) {
        if (len > 1) {
          Object[] copy = new Object[len - 1];
          System.arraycopy(elements,0, copy, 0, i);
          System.arraycopy(elements, i + 1, copy, i, len - i - 1);
          return new CyclicSequence<>(pos.get(), copy);
        } else {
          return new CyclicSequence<>();
        }
      }
    }
    return this;
  }

  @Override
  public boolean hasNext() {
    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T next() {
    int len = elements.length;
    switch (len) {
      case 0:
        return null;
      case 1:
        return (T) elements[0];
      default:
        int p = pos.getAndIncrement();
        if (p >= len) {
          p = p % len;
          if (p == 0) {
            pos.addAndGet(-len);
          }
        }
        return (T) elements[p];
    }
  }

  /**
   * @return the size of this sequence
   */
  public int size() {
    return elements.length;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterator<T> iterator() {
    return Arrays.<T>asList((T[]) elements).iterator();
  }
}

