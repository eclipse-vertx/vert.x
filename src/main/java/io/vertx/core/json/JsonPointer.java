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

package io.vertx.core.json;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.impl.JsonPointerImpl;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of <a href="https://tools.ietf.org/html/rfc6901">RFC6901</a> Json Pointers.
 *
 * @author Francesco Guardiani <a href="https://slinkydeveloper.github.io/">@slinkydeveloper</a>
 */
@VertxGen
public interface JsonPointer {

  /**
   * Alias for toString()
   *
   * @return
   */
  String build();

  /**
   * Build a URI representation of the JSON Pointer
   *
   * @return
   */
  String buildURI();

  /**
   * Append unescaped path to JsonPointer. <br/>
   * Note: If you provide escaped path the behaviour is undefined
   *
   * @param path unescaped path
   * @return
   */
  @Fluent JsonPointer append(String path);

  /**
   * Append unescaped list of paths to JsonPointer <br/>
   * Note: If you provide escaped paths the behaviour is undefined
   *
   * @param paths unescaped paths
   * @return
   */
  @Fluent JsonPointer append(List<String> paths);

  /**
   * Query the provided object. <br/>
   * Note: if this pointer is a root pointer, this function returns the provided object
   *
   * @param object the object to query
   * @return null if pointer points to not existing value, otherwise the requested value
   */
  @Nullable Object query(Object object);

  /**
   * Write a value in the selected pointer. The path token "-" is handled as append to end of array. <br/>
   * This function does not support root pointers.
   *
   * @param object object to query and write
   * @param value object to insert
   * @return true if the write is completed, false otherwise
   * @throws IllegalStateException if the pointer is a root pointer
   */
  boolean writeObject(JsonObject object, Object value);

  /**
   * Write a value in the selected pointer. The path token "-" is handled as append to end of array. <br/>
   * This function does not support root pointers.
   *
   * @param array
   * @param value
   * @return true if the write is completed, false otherwise
   * @throws IllegalStateException if the pointer is a root pointer
   */
  boolean writeArray(JsonArray array, Object value);

  /**
   * Copy this JsonPointer instance
   *
   * @return copy of this json pointer instance
   */
  JsonPointer copy();

  /**
   * Build an empty JsonPointer
   *
   * @return a new empty JsonPointer
   */
  static JsonPointer create() {
    return new JsonPointerImpl();
  }

  /**
   * Build a JsonPointer from a json pointer string
   *
   * @param pointer the string representing a pointer
   * @return new instance of JsonPointer
   * @throws java.lang.IllegalArgumentException if the pointer provided is not valid
   */
  static JsonPointer from(String pointer) {
    return new JsonPointerImpl(pointer);
  }

  /**
   * Build a JsonPointer from a URI
   *
   * @param uri uri representing a json pointer
   * @return new instance of JsonPointer
   */
  static JsonPointer fromURI(String uri) {
    return new JsonPointerImpl(Arrays.asList(URI.create(uri).getFragment().split("/", -1)));
  }

}
