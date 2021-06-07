/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("rawtypes")
public class ClusteredEventBusHandlers implements Handlers {

  private static final HandlerHolder[] EMPTY_ARRAY = new HandlerHolder[0];

  private final HandlerHolder[] elements;
  private final int localOnlyOffset;
  private final AtomicInteger pos;

  public ClusteredEventBusHandlers() {
    this(EMPTY_ARRAY, 0, 0);
  }

  private ClusteredEventBusHandlers(HandlerHolder[] elements, int localOnlyOffset, int pos) {
    this.elements = elements;
    this.localOnlyOffset = localOnlyOffset;
    this.pos = new AtomicInteger(pos);
  }

  @Override
  public Handlers add(HandlerHolder holder) {
    int len = elements.length;
    HandlerHolder[] copy = new HandlerHolder[len + 1];
    int offset;
    if (holder.localOnly) {
      offset = localOnlyOffset;
      copy[len] = holder;
      System.arraycopy(elements, 0, copy, 0, len);
    } else {
      offset = localOnlyOffset + 1;
      copy[0] = holder;
      System.arraycopy(elements, 0, copy, 1, len);
    }
    return new ClusteredEventBusHandlers(copy, offset, pos.get());
  }

  @Override
  public Handlers remove(HandlerHolder holder) {
    int len = elements.length;
    for (int i = 0; i < len; i++) {
      if (Objects.equals(holder, elements[i])) {
        if (len > 1) {
          HandlerHolder[] copy = new HandlerHolder[len - 1];
          System.arraycopy(elements, 0, copy, 0, i);
          System.arraycopy(elements, i + 1, copy, i, len - i - 1);
          return new ClusteredEventBusHandlers(copy, Math.min(i, localOnlyOffset), pos.get() % copy.length);
        } else {
          return new ClusteredEventBusHandlers();
        }
      }
    }
    return this;
  }

  @Override
  public boolean isEmpty() {
    return elements.length == 0;
  }

  @Override
  public HandlerHolder next(boolean includeLocalOnly) {
    int len = count(includeLocalOnly);
    switch (len) {
      case 0:
        return null;
      case 1:
        return elements[0];
      default:
        int p;
        p = pos.getAndIncrement();
        return elements[Math.abs(p % len)];
    }
  }

  @Override
  public int count(boolean includeLocalOnly) {
    return includeLocalOnly ? elements.length : localOnlyOffset;
  }

  @Override
  public Iterator<HandlerHolder> iterator(boolean includeLocalOnly) {
    return new Iterator<HandlerHolder>() {
      int index;

      @Override
      public boolean hasNext() {
        return index < count(includeLocalOnly);
      }

      @Override
      public HandlerHolder next() {
        return elements[index++];
      }
    };
  }
}
