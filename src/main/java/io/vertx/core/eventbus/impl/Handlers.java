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

package io.vertx.core.eventbus.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Handlers implements Iterable<HandlerHolder> {

  private final AtomicInteger pos;
  private final HandlerHolder[] elements;

  Handlers(HandlerHolder holder) {
    this(new AtomicInteger(0), new HandlerHolder[]{holder});
  }

  private Handlers(AtomicInteger pos, HandlerHolder[] elements) {
    this.pos = pos;
    this.elements = elements;
  }

  HandlerHolder first() {
    return elements[0];
  }

  Handlers add(HandlerHolder holder) {
    int len = elements.length;
    HandlerHolder[] copy = Arrays.copyOf(elements, len + 1);
    copy[len] = holder;
    return new Handlers(pos, copy);
  }

  Handlers remove(HandlerHolder holder) {
    int len = elements.length;
    for (int i = 0;i < len;i++) {
      if (holder == elements[i]) {
        if (len > 1) {
          HandlerHolder[] copy = new HandlerHolder[len - 1];
          System.arraycopy(elements,0, copy, 0, i);
          System.arraycopy(elements, i + 1, copy, i, len - i - 1);
          return new Handlers(pos, copy);
        } else {
          return null;
        }
      }
    }
    return this;
  }

  public HandlerHolder choose() {
    while (true) {
      int size = elements.length;
      if (size == 0) {
        return null;
      }
      int p = pos.getAndIncrement();
      if (p >= size - 1) {
        pos.set(0);
      }
      try {
        return elements[p];
      } catch (ArrayIndexOutOfBoundsException e) {
        // Can happen
        pos.set(0);
      }
    }
  }

  public int size() {
    return elements.length;
  }

  @Override
  public Iterator<HandlerHolder> iterator() {
    return Arrays.asList(elements).iterator();
  }
}

