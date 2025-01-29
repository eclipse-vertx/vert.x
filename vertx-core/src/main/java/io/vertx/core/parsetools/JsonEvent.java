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

package io.vertx.core.parsetools;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;

/**
 * A JSON event emitted by the {@link JsonParser}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public interface JsonEvent {

  /**
   * @return the type of the event
   */
  JsonEventType type();

  /**
   * @return the name of the field when the event is emitted as a JSON object member
   */
  String fieldName();

  /**
   * @return the json value for {@link JsonEventType#VALUE} events
   */
  Object value();

  /**
   * @return true when the JSON value is a number
   */
  boolean isNumber();

  /**
   * @return the {@code Integer} value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not an {@code Integer}
   */
  Integer integerValue();

  /**
   * @return the {@code Long} value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a {@code Long}
   */
  Long longValue();

  /**
   * @return the {@code Float} value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a {@code Float}
   */
  Float floatValue();

  /**
   * @return the {@code Double} value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a {@code Double}
   */
  Double doubleValue();

  /**
   * @return true when the JSON value is a boolean
   */
  boolean isBoolean();

  /**
   * @return the {@code Boolean} value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a {@code Boolean}
   */
  Boolean booleanValue();

  /**
   * @return true when the JSON value is a string
   */
  boolean isString();

  /**
   * @return the string value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a string
   */
  String stringValue();

  /**
   * Return the binary value.
   * <p>
   * JSON itself has no notion of a binary, this extension complies to the RFC-7493, so this method assumes there is a
   * String value with the key and it contains a Base64 encoded binary, which it decodes if found and returns.
   *
   * @return the binary value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a String
   * @throws java.lang.IllegalArgumentException if the String value is not a legal Base64 encoded value
   */
  Buffer binaryValue();

  /**
   * Return the {@code Instant} value.
   * <p>
   * JSON itself has no notion of a temporal types, this extension complies to the RFC-7493, so this method assumes
   * there is a String value with the key and it contains an ISO 8601 encoded date and time format
   * such as "2017-04-03T10:25:41Z", which it decodes if found and returns.
   *
   * @return the {@code Instant} value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a String
   * @throws java.time.format.DateTimeParseException if the String value is not a legal ISO 8601 encoded value
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Instant instantValue();

  /**
   * @return true when the JSON value is null
   */
  boolean isNull();

  /**
   * @return true when the JSON value is a JSON object
   */
  boolean isObject();

  /**
   * @return the JSON object value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a JSON object
   */
  JsonObject objectValue();

  /**
   * @return true when the JSON value is a JSON array
   */
  boolean isArray();

  /**
   * @return the JSON array value or {@code null} if the event has no JSON value
   * @throws java.lang.ClassCastException if the value is not a JSON array
   */
  JsonArray arrayValue();

  /**
   * Decodes and returns the current value as the specified {@code type}.
   *
   * @param type the type to decode the value to
   * @return the decoded value
   */
  <T> T mapTo(Class<T> type);

}
