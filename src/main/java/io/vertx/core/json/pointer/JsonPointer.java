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

package io.vertx.core.json.pointer;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.pointer.impl.JsonPointerImpl;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Implementation of <a href="https://tools.ietf.org/html/rfc6901">RFC6901 Json Pointers</a>.
 *
 * @author Francesco Guardiani <a href="https://slinkydeveloper.github.io/">@slinkydeveloper</a>
 */
@VertxGen
public interface JsonPointer {

  /**
   * Return {@code true} if the pointer is a root pointer
   */
  boolean isRootPointer();

  /**
   * Return {@code true} if the pointer is local (URI with only fragment)
   */
  boolean isLocalPointer();

  /**
   * Return {@code true} if this pointer is a parent pointer of {@code child}.
   * <br/>
   * For instance {@code "/properties"} pointer is parent pointer of {@code "/properties/parent"}
   *
   * @param child
   */
  boolean isParent(JsonPointer child);

  /**
   * Build a <a href="https://tools.ietf.org/html/rfc6901#section-5">string representation</a> of the JSON Pointer
   */
  @Override
  String toString();

  /**
   * Build a <a href="https://tools.ietf.org/html/rfc6901#section-6">URI representation</a> of the JSON Pointer
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  URI toURI();

  /**
   * Return the underlying URI without the fragment
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  URI getURIWithoutFragment();

  /**
   * Append an unescaped {@code token} to this pointer <br/>
   * Note: If you provide escaped path the behaviour is undefined
   *
   * @param token the unescaped reference token
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonPointer append(String token);

  /**
   * Append the {@code index} as reference token to JsonPointer
   *
   * @param index
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonPointer append(int index);

  /**
   * Append an unescaped list of {@code tokens} to JsonPointer <br/>
   * Note: If you provide escaped paths the behaviour is undefined
   *
   * @param tokens unescaped reference tokens
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonPointer append(List<String> tokens);

  /**
   * Append all tokens of {@code pointer} to this pointer <br/>
   * Note: The base URI of this pointer will remain untouched
   *
   * @param pointer other pointer
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonPointer append(JsonPointer pointer);

  /**
   * Remove last reference token of this pointer
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonPointer parent();

  /**
   * Query {@code objectToQuery} using the provided {@link JsonPointerIterator}. <br/>
   * If you need to query Vert.x json data structures, use {@link JsonPointer#queryJson(Object)}<br/>
   * Note: if this pointer is a root pointer, this function returns the provided object
   *
   * @param objectToQuery the object to query
   * @param iterator the json pointer iterator that provides the logic to access to the objectToQuery
   * @return null if pointer points to not existing value, otherwise the requested value
   */
  default @Nullable Object query(Object objectToQuery, JsonPointerIterator iterator) { return queryOrDefault(objectToQuery, iterator, null); }

  /**
   * Query {@code objectToQuery} using the provided {@link JsonPointerIterator}. If the query result is null, returns the default. <br/>
   * If you need to query Vert.x json data structures, use {@link JsonPointer#queryJsonOrDefault(Object, Object)}<br/>
   * Note: if this pointer is a root pointer, this function returns the provided object
   *
   * @param objectToQuery the object to query
   * @param iterator the json pointer iterator that provides the logic to access to the objectToQuery
   * @param defaultValue default value if query result is null
   * @return null if pointer points to not existing value, otherwise the requested value
   */
  Object queryOrDefault(Object objectToQuery, JsonPointerIterator iterator, Object defaultValue);

  /**
   * Query {@code jsonElement}. <br/>
   * Note: if this pointer is a root pointer, this function returns the provided json element
   *
   * @param jsonElement the json element to query
   * @return null if pointer points to not existing value, otherwise the requested value
   */
  default @Nullable Object queryJson(Object jsonElement) {return query(jsonElement, JsonPointerIterator.JSON_ITERATOR); }

  /**
   * Query {@code jsonElement}. If the query result is null, returns the default.<br/>
   * Note: if this pointer is a root pointer, this function returns the provided object
   *
   * @param jsonElement the json element to query
   * @param defaultValue default value if query result is null
   * @return null if pointer points to not existing value, otherwise the requested value
   */
  default @Nullable Object queryJsonOrDefault(Object jsonElement, Object defaultValue) {return queryOrDefault(jsonElement, JsonPointerIterator.JSON_ITERATOR, defaultValue); }

  /**
   * Query {@code objectToQuery} tracing each element walked during the query, including the first and the result (if any).<br/>
   * The first element of the list is objectToQuery and the last is the result, or the element before the first null was encountered
   *
   * @param objectToQuery the object to query
   * @param iterator the json pointer iterator that provides the logic to access to the objectToQuery
   * @return the stream of walked elements
   */
  List<Object> tracedQuery(Object objectToQuery, JsonPointerIterator iterator);

  /**
   * Write {@code newElement} in {@code objectToWrite} using this pointer. The path token "-" is handled as append to end of array <br/>
   * If you need to write in Vert.x json data structures, use {@link JsonPointer#writeJson(Object, Object)} (Object)}<br/>
   *
   * @param objectToWrite object to write
   * @param iterator the json pointer iterator that provides the logic to access to the objectToMutate
   * @param newElement  object to insert
   * @param createOnMissing create objects when missing a object key or an array index
   * @return a reference to objectToWrite if the write was completed, a reference to newElement if the pointer is a root pointer, null if the write failed
   */
  Object write(Object objectToWrite, JsonPointerIterator iterator, Object newElement, boolean createOnMissing);

  /**
   * Write {@code newElement} in {@code jsonElement} using this pointer. The path token "-" is handled as append to end of array.
   *
   * @param jsonElement json element to query and write
   * @param newElement json to insert
   * @return a reference to json if the write was completed, a reference to newElement if the pointer is a root pointer, null if the write failed
   */
  default Object writeJson(Object jsonElement, Object newElement) { return writeJson(jsonElement, newElement, false); }

  /**
   * Write {@code newElement} in {@code jsonElement} using this pointer. The path token "-" is handled as append to end of array.
   *
   * @param jsonElement json to query and write
   * @param newElement json to insert
   * @param createOnMissing create JsonObject when missing a object key or an array index
   * @return a reference to json if the write was completed, a reference to newElement if the pointer is a root pointer, null if the write failed
   */
  default Object writeJson(Object jsonElement, Object newElement, boolean createOnMissing) {
    return write(jsonElement, JsonPointerIterator.JSON_ITERATOR, newElement, createOnMissing);
  }

  /**
   * Copy a JsonPointer
   *
   * @return a copy of this pointer
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
   * @throws IllegalArgumentException if the pointer provided is not valid
   */
  static JsonPointer from(String pointer) {
    return new JsonPointerImpl(pointer);
  }

  /**
   * Build a JsonPointer from a URI.
   *
   * @param uri uri representing a json pointer
   * @return new instance of JsonPointer
   * @throws IllegalArgumentException if the pointer provided is not valid
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static JsonPointer fromURI(URI uri) {
    return new JsonPointerImpl(uri);
  }

}
