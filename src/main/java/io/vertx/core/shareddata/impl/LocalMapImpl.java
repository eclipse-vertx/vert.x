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

package io.vertx.core.shareddata.impl;

import io.vertx.core.shareddata.LocalMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class LocalMapImpl<K, V> extends AbstractLocalMapImpl<K, V, LocalMap<?, ?>> {

  private final ConcurrentMap<String, LocalMap<?, ?>> maps;
  private final String name;
  private final ConcurrentMap<K, V> map = new ConcurrentHashMap<>();

  LocalMapImpl(String name, ConcurrentMap<String, LocalMap<?, ?>> maps) {
    this.name = name;
    this.maps = maps;
  }

  @Override
  String getName() {
    return name;
  }

  @Override
  ConcurrentMap<K, V> getInternalMap() {
    return map;
  }

  @Override
  ConcurrentMap<String, LocalMap<?, ?>> getAllLocalMaps() {
    return maps;
  }

}
