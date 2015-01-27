/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class represents a MultiMap of String keys to a List of String values.
 * <p>
 * It's useful in Vert.x to represent things in Vert.x like HTTP headers and HTTP parameters which allow
 * multiple values for keys.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface MultiMap extends Iterable<Map.Entry<String, String>> {

  @GenIgnore
  String get(CharSequence name);

  /**
   * Returns the value of with the specified name.  If there are
   * more than one values for the specified name, the first value is returned.
   *
   * @param name The name of the header to search
   * @return The first header value or {@code null} if there is no such entry
   */
  String get(String name);

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
  @GenIgnore
  List<String> getAll(CharSequence name);

  /**
   * Returns all entries in the multi-map.
   *
   * @return A immutable {@link java.util.List} of the name-value entries, which will be
   *         empty if no pairs are found
   */
  @GenIgnore
  List<Map.Entry<String, String>> entries();

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
  @GenIgnore
  boolean contains(CharSequence name);

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
  @GenIgnore
  MultiMap add(CharSequence name, CharSequence value);

  /**
   * Adds a new values under the specified name
   *
   * @param name The name being set
   * @param values The values
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  MultiMap add(String name, Iterable<String> values);

  /**
   * Like {@link #add(String, Iterable)} but accepting {@code CharSequence} as parameters
   */
  @GenIgnore
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
  @GenIgnore
  MultiMap addAll(Map<String, String> headers);

  /**
   * Sets a value under the specified name.
   * <p>
   * If there is an existing header with the same name, it is removed.
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
  @GenIgnore
  MultiMap set(CharSequence name, CharSequence value);

  /**
   * Sets values for the specified name.
   *
   * @param name The name of the headers being set
   * @param values The values of the headers being set
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  MultiMap set(String name, Iterable<String> values);

  /**
   * Like {@link #set(String, Iterable)} but accepting {@code CharSequence} as parameters
   */
  @GenIgnore
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
  @GenIgnore
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
  @GenIgnore
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
