/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface MultiMap extends Iterable<Map.Entry<String, String>> {

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
   * @return A immutable {@link List} of values which will be empty if no values
   *         are found
   */
  List<String> getAll(String name);

  /**
   * Returns all entries it contains.
   *
   * @return A immutable {@link List} of the name-value entries, which will be
   *         empty if no pairs are found
   */
  List<Map.Entry<String, String>> entries();

  /**
   * Checks to see if there is a value with the specified name
   *
   * @param name The name to search for
   * @return True if at least one entry is found
   */
  boolean contains(String name);

  /**
   * Return true if empty
   */
  boolean isEmpty();

  /**
   * Gets a immutable {@link Set} of all names
   *
   * @return A {@link Set} of all names
   */
  Set<String> names();

  /**
   * Adds a new value with the specified name and value.
   *
   * If the specified value is not a {@link String}, it is converted
   * into a {@link String} by {@link Object#toString()}.
   *
   * @param name The name
   * @param value The value being added
   *
   * @return {@code this}
   */
  MultiMap add(String name, String value);

  /**
   * Adds a new values under the specified name
   *
   *
   * @param name The name being set
   * @param values The values
   * @return {@code this}
   */
  MultiMap add(String name, Iterable<String> values);

  /**
   * Adds all the entries from another MultiMap to this one
   *
   * @return {@code this}
   */
  MultiMap add(MultiMap headers);

  /**
   * Adds all the entries from a Map to this
   *
   * @return {@code this}
   */
  MultiMap add(Map<String, String> headers);

  /**
   * Sets a value under the specified name.
   *
   * If there is an existing header with the same name, it is removed.
   *
   * @param name The name
   * @param value The value
   * @return {@code this}
   */
  MultiMap set(String name, String value);

  /**
   * Sets values for the specified name.
   *
   * @param name The name of the headers being set
   * @param values The values of the headers being set
   * @return {@code this}
   */
  MultiMap set(String name, Iterable<String> values);

  /**
   * Cleans this instance.
   *
   * @return {@code this}
   */
  MultiMap set(MultiMap headers);

  /**
   * Cleans and set all values of the given instance
   *
   * @return {@code this}
   */
  MultiMap set(Map<String, String> headers);

 /**
  * Removes the value with the given name
  *
  * @param name The name  of the value to remove
  * @return {@code this}
  */
  MultiMap remove(String name);

  /**
   * Removes all
   *
   * @return {@code this}
   */
  MultiMap clear();

  /**
   * Return the number of names.
   */
  int size();
}
