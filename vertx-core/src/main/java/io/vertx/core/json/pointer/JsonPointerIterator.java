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

package io.vertx.core.json.pointer;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.pointer.impl.JsonPointerIteratorImpl;

/**
 * The JsonPointerIterator is used by the read/write algorithms of the {@link JsonPointer} to read/write the querying data structure <br/>
 *
 * Every method takes the currentValue as parameter, representing the actual value held by the query algorithm.<br/>
 *
 * Implementations of this interface should be stateless, so they can be reused<br/>
 *
 * You can implement this interface to query the structure you want using json pointers
 *
 * @author Francesco Guardiani <a href="https://slinkydeveloper.github.io/">@slinkydeveloper</a>
 *
 */
@VertxGen
public interface JsonPointerIterator {

  /**
   * @param currentValue
   * @return {@code true} if the current value is a queryable object
   */
  boolean isObject(@Nullable Object currentValue);

  /**
   * @param currentValue
   * @return {@code true} if the current value is a queryable array
   */
  boolean isArray(@Nullable Object currentValue);

  /**
   * @param currentValue
   * @return {@code true} if the current value is null/empty
   */
  boolean isNull(@Nullable Object currentValue);

  /**
   * @param currentValue
   * @param key object key
   * @return {@code true} if current value is a queryable object that contains the specified key
   */
  boolean objectContainsKey(@Nullable Object currentValue, String key);

  /**
   * Returns the object parameter with specified key.
   *
   * @param currentValue
   * @param key object key
   * @param createOnMissing If the current value is an object that doesn't contain the key, put an empty object at provided key
   * @return the requested object parameter, or null if the method was not able to find it
   */
  Object getObjectParameter(@Nullable Object currentValue, String key, boolean createOnMissing);

  /**
   * Move the iterator the array element at specified index
   *
   * @param currentValue
   * @param i array index
   * @return the request array element, or null if the method was not able to find it
   */
  Object getArrayElement(@Nullable Object currentValue, int i);

  /**
   * Write object parameter at specified key
   *
   * @param currentValue
   * @param key
   * @param value
   * @return true if the operation is successful
   */
  boolean writeObjectParameter(@Nullable Object currentValue, String key, @Nullable Object value);

  /**
   * Write array element at specified index
   *
   * @param currentValue
   * @param i
   * @param value
   * @return true if the operation is successful
   */
  boolean writeArrayElement(@Nullable Object currentValue, int i, @Nullable Object value);

  /**
   * Append array element
   *
   * @param currentValue
   * @param value
   * @return true if the operation is successful
   */
  boolean appendArrayElement(@Nullable Object currentValue, @Nullable Object value);

  /**
   * Instance of a JsonPointerIterator to query Vert.x Json structures
   */
  JsonPointerIterator JSON_ITERATOR = new JsonPointerIteratorImpl();

}
