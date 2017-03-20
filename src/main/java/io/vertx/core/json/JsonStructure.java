/*
 * Copyright (c) 2011-2017 The original author or authors
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
package io.vertx.core.json;

import io.vertx.core.shareddata.impl.ClusterSerializable;

/**
 * A representation of a <a href="http://json.org/">JSON</a> structure in Java.
 * 
 * JSON is built on two structures:
 *
 * A collection of name/value pairs. In various languages, this is realized as
 * an object, record, struct, dictionary, hash table, keyed list, or associative
 * array. An ordered list of values. In most languages, this is realized as an
 * array, vector, list, or sequence.
 *
 * @author Jan Zajic
 *
 */
public abstract class JsonStructure implements ClusterSerializable {

  /**
   * Get the number of entries in this JSON structure
   *
   * @return the number of items
   */
  public abstract int size();

  /**
   * Encode the JSON structure to a string
   *
   * @return the string encoding
   */
  public abstract String encode();

  /**
   * Encode the JSON structure prettily as a string
   *
   * @return the string encoding
   */
  public abstract String encodePrettily();

  /**
   * Are there zero entries in this JSON structure?
   *
   * @return true if zero, false otherwise
   */
  public abstract boolean isEmpty();

  /**
   * Remove all entries from the JSON structure
   *
   * @return a reference to this, so the API can be used fluently
   */
  public abstract JsonStructure clear();

  /**
   * Make a copy of the JSON structure
   *
   * @return a copy
   */
  public abstract JsonStructure copy();

  public boolean isArray() {
    return this instanceof JsonArray;
  }

  public boolean isObject() {
    return this instanceof JsonObject;
  }

  public JsonArray asArray() {
    return (JsonArray) this;
  }

  public JsonObject asObject() {
    return (JsonObject) this;
  }

}
