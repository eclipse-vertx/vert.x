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

package org.vertx.java.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface MultiMap extends Iterable<Map.Entry<String, String>> {

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
   * @return A immutable {@link List} of values which will be empty if no values
   *         are found
   */
  List<String> getAll(String name);
  List<String> getAll(CharSequence name);


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
  boolean contains(CharSequence name);

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
   *
   * @param name The name
   * @param value The value being added
   *
   * @return {@code this}
   */
  MultiMap add(String name, String value);
  MultiMap add(CharSequence name, CharSequence value);

  /**
   * Adds a new values under the specified name
   *
   *
   * @param name The name being set
   * @param values The values
   * @return {@code this}
   */
  MultiMap add(String name, Iterable<String> values);
  MultiMap add(CharSequence name, Iterable<CharSequence> values);

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
  MultiMap set(CharSequence name, CharSequence value);

  /**
   * Sets values for the specified name.
   *
   * @param name The name of the headers being set
   * @param values The values of the headers being set
   * @return {@code this}
   */
  MultiMap set(String name, Iterable<String> values);
  MultiMap set(CharSequence name, Iterable<CharSequence> values);


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
  MultiMap remove(CharSequence name);


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
