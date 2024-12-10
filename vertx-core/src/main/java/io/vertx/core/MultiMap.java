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

package io.vertx.core;

import io.vertx.codegen.annotations.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * This class represents a MultiMap of String keys to a List of String values.
 * <p>
 * It's useful in Vert.x to represent things in Vert.x like HTTP headers and HTTP parameters which allow
 * multiple values for keys.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public interface MultiMap extends Iterable<Map.Entry<String, String>> {

  /**
   * Create a multi-map implementation with case insensitive keys, for instance it can be used to hold some HTTP headers.
   *
   * @return the multi-map
   */
  static MultiMap caseInsensitiveMultiMap() {
    return HeadersMultiMap.caseInsensitive();
  }

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  String get(CharSequence name);

  /**
   * Returns the value of with the specified name.  If there are
   * more than one values for the specified name, the first value is returned.
   *
   * @param name The name of the header to search
   * @return The first header value or {@code null} if there is no such entry
   */
  @Nullable String get(String name);

  /**
   * Returns the values with the specified name
   *
   * @param name The name to search
   * @return A immutable {@link java.util.List} of values which will be empty if no values
   *         are found
   */
  List<String> getAll(String name);

  /**
   * Like {@link #getAll(String)} but accepting a {@code CharSequence} as a parameter
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  List<String> getAll(CharSequence name);

  /**
   * Allows iterating over the entries in the map
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default void forEach(BiConsumer<String, String> action) {
    forEach(new Consumer<Map.Entry<String, String>>() {
      @Override
      public void accept(Map.Entry<String, String> entry) {
        action.accept(entry.getKey(), entry.getValue());
      }
    });
  }

  /**
   * Returns all entries in the multi-map.
   *
   * @return A immutable {@link java.util.List} of the name-value entries, which will be
   *         empty if no pairs are found
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default List<Map.Entry<String, String>> entries() {
    List<Map.Entry<String, String>> entries = new ArrayList<>();
    forEach((Consumer<Map.Entry<String, String>>)entries::add);
    return entries;
  }

  /**
   * Checks to see if there is a value with the specified name
   *
   * @param name The name to search for
   * @return true if at least one entry is found
   */
  boolean contains(String name);

  /**
   * Like {@link #contains(String)} but accepting a {@code CharSequence} as a parameter
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  boolean contains(CharSequence name);

  /**
   * Check if there is a header with the specified {@code name} and {@code value}.
   *
   * If {@code caseInsensitive} is {@code true}, {@code value} is compared in a case-insensitive way.
   *
   * @param name the name to search for
   * @param value the value to search for
   * @return {@code true} if at least one entry is found
   */
  default boolean contains(String name, String value, boolean caseInsensitive) {
    return getAll(name).stream()
      .anyMatch(val -> caseInsensitive ? val.equalsIgnoreCase(value) : val.equals(value));
  }

  /**
   * Like {@link #contains(String, String, boolean)} but accepting {@code CharSequence} parameters.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
    Predicate<String> predicate;
    if (caseInsensitive) {
      String valueAsString = value.toString();
      predicate = val -> val.equalsIgnoreCase(valueAsString);
    } else {
      predicate = val -> val.contentEquals(value);
    }
    return getAll(name).stream().anyMatch(predicate);
  }

  /**
   * Return true if empty
   */
  boolean isEmpty();

  /**
   * Gets a immutable {@link java.util.Set} of all names
   *
   * @return A {@link java.util.Set} of all names
   */
  Set<String> names();

  /**
   * Adds a new value with the specified name and value.
   *
   * @param name The name
   * @param value The value being added
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MultiMap add(String name, String value);

  /**
   * Like {@link #add(String, String)} but accepting {@code CharSequence} as parameters
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap add(CharSequence name, CharSequence value);

  /**
   * Adds a new values under the specified name
   *
   * @param name The name being set
   * @param values The values
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap add(String name, Iterable<String> values);

  /**
   * Like {@link #add(String, Iterable)} but accepting {@code CharSequence} as parameters
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap add(CharSequence name, Iterable<CharSequence> values);

  /**
   * Adds all the entries from another MultiMap to this one
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MultiMap addAll(MultiMap map);

  /**
   * Adds all the entries from a Map to this
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap addAll(Map<String, String> headers);

  /**
   * Sets a {@code value} under the specified {@code name}.
   * <p>
   * If there is an existing header with the same name, it is removed. Setting a {@code null} value removes the entry.
   *
   * @param name The name
   * @param value The value
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MultiMap set(String name, String value);

  /**
   * Like {@link #set(String, String)} but accepting {@code CharSequence} as parameters
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap set(CharSequence name, CharSequence value);

  /**
   * Sets values for the specified name.
   *
   * @param name The name of the headers being set
   * @param values The values of the headers being set
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap set(String name, Iterable<String> values);

  /**
   * Like {@link #set(String, Iterable)} but accepting {@code CharSequence} as parameters
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap set(CharSequence name, Iterable<CharSequence> values);

  /**
   * Cleans this instance.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MultiMap setAll(MultiMap map);

  /**
   * Cleans and set all values of the given instance
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap setAll(Map<String, String> headers);

 /**
  * Removes the value with the given name
  *
  * @param name The name  of the value to remove
  * @return a reference to this, so the API can be used fluently
  */
  @Fluent
  MultiMap remove(String name);

  /**
   * Like {@link #remove(String)} but accepting {@code CharSequence} as parameters
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Fluent
  MultiMap remove(CharSequence name);

  /**
   * Removes all
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MultiMap clear();

  /**
   * Return the number of keys.
   */
  int size();

}
