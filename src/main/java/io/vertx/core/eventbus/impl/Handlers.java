/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Handlers {

  private final AtomicInteger pos = new AtomicInteger(0);
  public final List<HandlerHolder> list = new CopyOnWriteArrayList<>();

  public HandlerHolder choose() {
    while (true) {
      int size = list.size();
      if (size == 0) {
        return null;
      }
      int p = pos.getAndIncrement();
      if (p >= size - 1) {
        pos.set(0);
      }
      try {
        return list.get(p);
      } catch (IndexOutOfBoundsException e) {
        // Can happen
        pos.set(0);
      }
    }
  }
}

