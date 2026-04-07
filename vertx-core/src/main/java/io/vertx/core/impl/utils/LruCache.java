/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple LRU cache based, this class is not thread safe.
 */
public class LruCache<K, V> extends LinkedHashMap<K, V> {

  private final int maxSize;

  public LruCache(int maxSize) {
    super(8, 0.75f, true);
    if (maxSize < 1) {
      throw new UnsupportedOperationException();
    }
    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maxSize;
  }
}
