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
package io.vertx.core.json.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Shareable;

import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * Implementation utilities (details) affecting the way JSON objects are wrapped.
 */
public final class JsonUtil {

  public static final Base64.Encoder BASE64_ENCODER;
  public static final Base64.Decoder BASE64_DECODER;

  private static final boolean LEGACY_TO_STRING;

  static {
    /*
     * Vert.x 3.x Json supports RFC-7493, however the JSON encoder/decoder format was incorrect.
     * Users who might need to interop with Vert.x 3.x applications should set the system property
     * {@code vertx.json.base64} to {@code legacy}.
     */
    if ("legacy".equalsIgnoreCase(System.getProperty("vertx.json.base64"))) {
      BASE64_ENCODER = Base64.getEncoder();
      BASE64_DECODER = Base64.getDecoder();
    } else {
      BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
      BASE64_DECODER = Base64.getUrlDecoder();
    }

    /*
     * Vert.x 4.x Json toString() methods rely on the CODEC. This can be dangerous when circular references
     * are present in the object graph, for this reason these methods will encode with a simpler inspector
     * mode that hints about circular references and avoids encoding very large objects by truncating elements
     * or deeply nested objects/arrays. Users who might need to interop with Vert.x 4.x applications and expect
     * toString() to be an alias to `encode()` should set the system property {@code vertx.json.tostring} to
     *  {@code legacy}.
     */
    LEGACY_TO_STRING = "legacy".equalsIgnoreCase(System.getProperty("vertx.json.tostring"));
  }

  /**
   * Wraps well known java types to adhere to the Json expected types.
   * <ul>
   *   <li>{@code Map} will be wrapped to {@code JsonObject}</li>
   *   <li>{@code List} will be wrapped to {@code JsonArray}</li>
   *   <li>{@code Instant} will be converted to iso date {@code String}</li>
   *   <li>{@code byte[]} will be converted to base64 {@code String}</li>
   *   <li>{@code Enum} will be converted to enum name {@code String}</li>
   * </ul>
   *
   * @param val java type
   * @return wrapped type or {@code val} if not applicable.
   */
  public static Object wrapJsonValue(Object val) {
    if (val == null) {
      return null;
    }

    // perform wrapping
    if (val instanceof Map) {
      val = new JsonObject((Map) val);
    } else if (val instanceof List) {
      val = new JsonArray((List) val);
    } else if (val instanceof Instant) {
      val = ISO_INSTANT.format((Instant) val);
    } else if (val instanceof byte[]) {
      val = BASE64_ENCODER.encodeToString((byte[]) val);
    } else if (val instanceof Buffer) {
      val = BASE64_ENCODER.encodeToString(((Buffer) val).getBytes());
    } else if (val instanceof Enum) {
      val = ((Enum) val).name();
    }

    return val;
  }

  public static final Function<Object, ?> DEFAULT_CLONER = o -> {
    throw new IllegalStateException("Illegal type in Json: " + o.getClass());
  };

  @SuppressWarnings("unchecked")
  public static Object deepCopy(Object val, Function<Object, ?> copier) {
    if (val == null) {
      // OK
    } else if (val instanceof Number) {
      // OK
    } else if (val instanceof Boolean) {
      // OK
    } else if (val instanceof String) {
      // OK
    } else if (val instanceof Character) {
      // OK
    } else if (val instanceof CharSequence) {
      // CharSequences are not immutable, so we force toString() to become immutable
      val = val.toString();
    } else if (val instanceof Shareable) {
      // Shareable objects know how to copy themselves, this covers:
      // JsonObject, JsonArray, Buffer or any user defined type that can shared across the cluster
      val = ((Shareable) val).copy();
    } else if (val instanceof Map) {
      val = (new JsonObject((Map) val)).copy(copier);
    } else if (val instanceof List) {
      val = (new JsonArray((List) val)).copy(copier);
    } else if (val instanceof byte[]) {
      // OK
    } else if (val instanceof Instant) {
      // OK
    } else if (val instanceof Enum) {
      // OK
    } else {
      val = copier.apply(val);
    }
    return val;
  }

  public static <T> Stream<T> asStream(Iterator<T> sourceIterator) {
    Iterable<T> iterable = () -> sourceIterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  /**
   * Inspect a JsonObject. Inspect is a debug method that returns a string representation of the object. It will display
   * a JSON-like representation of the object. Keys and values are not escaped. When the object contains circular
   * references a warning is displayed {@code (Circular *int)} the integer value is the offending object hash code as
   * computed by: {@link System#identityHashCode(Object)}.
   *
   * The inspector will enforce some limits to avoid stack overflow errors. The limits are:
   *
   * <ul>
   *   <li>max nested level of objects and/or arrays: {@code 3} - The current object + 2 levels</li>
   *   <li>max length of arrays or object properties: {@code 100} - Ellipsis will be printed after</li>
   *   <li>max string length: {@code 10000} - Ellipsis will be printed after</li>
   * </ul>
   *
   * Note: If the system property {@code vertx.json.tostring} is set to {@code legacy} the method will return JSON
   * encoded string.
   *
   * @param obj the JsonObject to inspect
   * @return String representation
   */
  public static String inspect(JsonObject obj) {
    if (obj == null) {
      return "null";
    }
    if (LEGACY_TO_STRING) {
      return obj.encode();
    }
    return Inspector.inspect(obj, null);
  }

  /**
   * Inspect a JsonArray. Inspect is a debug method that returns a string representation of the object. It will display
   * a JSON-like representation of the object. Keys and values are not escaped. When the object contains circular
   * references a warning is displayed {@code (Circular *int)} the integer value is the offending object hash code as
   * computed by: {@link System#identityHashCode(Object)}.
   *
   * The inspector will enforce some limits to avoid stack overflow errors. The limits are:
   *
   * <ul>
   *   <li>max nested level of objects and/or arrays: {@code 3} - The current object + 2 levels</li>
   *   <li>max length of arrays or object properties: {@code 100} - Ellipsis will be printed after</li>
   *   <li>max string length: {@code 10000} - Ellipsis will be printed after</li>
   * </ul>
   *
   * Note: If the system property {@code vertx.json.tostring} is set to {@code legacy} the method will return JSON
   * encoded string.
   *
   * @param obj the JsonObject to inspect
   * @return String representation
   */
  public static String inspect(JsonArray obj) {
    if (obj == null) {
      return "null";
    }
    if (LEGACY_TO_STRING) {
      return obj.encode();
    }
    return Inspector.inspect(obj, null);
  }
}
