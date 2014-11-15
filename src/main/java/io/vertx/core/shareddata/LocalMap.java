/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface LocalMap<K, V> {

  V get(K key);

  V put(K key, V value);

  V remove(K key);

  void clear();

  int size();

  boolean isEmpty();

  V putIfAbsent(K key, V value);

  boolean removeIfPresent(K key, V value);

  boolean replaceIfPresent(K key, V oldValue, V newValue);

  V replace(K key, V value);

  void close();

}
