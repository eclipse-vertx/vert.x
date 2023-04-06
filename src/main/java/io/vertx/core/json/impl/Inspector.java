/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Implementation utilities (details) affecting the way JSON objects are debugged.
 */
final class Inspector {

  private Inspector() {
  }

  private static final class Stack {
    private final Object[] stack = new Object[MAX_DEEP];
    private int pos;

    Stack(Object first) {
      push(first);
    }

    void push(Object element) {
      stack[pos++] = element;
    }

    void pop() {
      --pos;
    }

    public boolean contains(Object needle) {
      for (int i = 0; i < pos; i++) {
        if (stack[i] == needle) {
          return true;
        }
      }
      return false;
    }

    public int size() {
      return pos;
    }
  }

  /**
   * The maximum allowed deepness of the object graph. To avoid stack overflow or complex serialization.
   */
  private static final int MAX_DEEP = 3;
  /**
   * The maximum allowed elements of an object or array. To avoid complex serialization.
   */
  private static final int MAX_ELEMENTS = 100;
  /**
   * The maximum allowed length of Strings. To avoid complex serialization.
   */
  private static final int MAX_STRING_LENGTH = 10000;

  /**
   * Inspects a JsonObject. Inspect is a string representation of the object. It will display a JSON-like representation
   * of the object. Keys and values are not escaped. When the object contains circular references a warning is
   * displayed.
   *
   * @param obj the JsonObject to inspect or {@code null}.
   * @param seen the stack of seen objects or {@code null} for lazy init.
   * @return String representation of the object.
   */
  static String inspect(JsonObject obj, Stack seen) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');

    int count = 0;
    for (Map.Entry<String, Object> entry : obj) {
      if (count >= MAX_ELEMENTS) {
        sb.append(", ...");
        break;
      }
      if (count > 0) {
        sb.append(", ");
      }
      sb.append(entry.getKey());
      sb.append(": ");
      format(obj.getMap(), entry.getValue(), sb, seen);
      count++;
    }

    sb.append('}');
    return sb.toString();
  }

  /**
   * Inspects a JsonArray. Inspect is a string representation of the object. It will display a JSON-like representation
   * of the object. Keys and values are not escaped. When the object contains circular references a warning is
   * displayed.
   *
   * @param obj the JsonArray to inspect or {@code null}.
   * @param seen the stack of seen objects or {@code null} for lazy init.
   * @return String representation of the object.
   */
  static String inspect(JsonArray obj, Stack seen) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');

    for (int i = 0; i < obj.size(); i++) {
      if (i >= MAX_ELEMENTS) {
        sb.append(", ...");
        break;
      }
      if (i > 0) {
        sb.append(", ");
      }
      format(obj.getList(), obj.getValue(i), sb, seen);
    }

    sb.append(']');
    return sb.toString();
  }

  /**
   * Formats the object to a string. Follows the basic rules of JSON encoding with some freedoms:
   * <ul>
   *   <li>{@code null}, {@link Number} and {@link Boolean} are not quoted</li>
   *   <li>{@link JsonObject} and {@link JsonArray} are limited in nested elements</li>
   *   <li>{@link JsonObject} and {@link JsonArray} are limited in number of elements/properties</li>
   *   <li>{@link JsonObject} and {@link JsonArray} trace circular references</li>
   *   <li>Strings are not escaped</li>
   *   <li>Strings are limited in length</li>
   *   <li>Any other type is handled as a String using {@link Object#toString()}</li>
   * </ul>
   */
  private static void format(Object parent, Object obj, StringBuilder sb, Stack seen) {
    if (obj == null) {
      sb.append("null");
    } else if (obj instanceof Boolean) {
      sb.append(obj);
    } else if (obj instanceof Number) {
      sb.append(obj);
    } else if (obj instanceof JsonObject) {
      // unwrap the vert.x type
      Map<String, ?> container = ((JsonObject) obj).getMap();
      if (continueInspecting(sb, seen, container, '{', '}')) {
        if (seen == null) {
          seen = new Stack(parent);
        }
        seen.push(container);
        sb.append(inspect((JsonObject) obj, seen));
        seen.pop();
      }
    } else if (obj instanceof JsonArray) {
      // unwrap the vert.x type
      List<?> container = ((JsonArray) obj).getList();
      if (continueInspecting(sb, seen, container, '[', ']')) {
        if (seen == null) {
          seen = new Stack(parent);
        }
        seen.push(container);
        sb.append(inspect((JsonArray) obj, seen));
        seen.pop();
      }
    } else {
      sb.append('"');
      String str = obj.toString();
      if (str.length() >= MAX_STRING_LENGTH) {
        sb.append(str, 0, MAX_STRING_LENGTH);
        sb.append("...");
      } else {
        sb.append(str);
      }
      sb.append('"');
    }
  }

  /**
   * Checks if the container should be inspected further or not. Stop conditions are:
   *
   * <ul>
   *   <li>the container is already in the stack - meaning we found a circular reference</li>
   *   <li>the stack is too deep - meaning we are inspecting too many nested objects</li>
   * </ul>
   *
   * Given that the stack is lazy initialized, this method will return {@code true} if the stack is {@code null}.
   */
  private static boolean continueInspecting(StringBuilder sb, Stack seen, Object container, char open, char close) {
    if (seen == null) {
      return true;
    }

    if (seen.size() >= MAX_DEEP) {
      sb
        .append(open)
        .append("...")
        .append(close);
      return false;
    }

    if (seen.contains(container)) {
      sb
        .append(open)
        .append("Circular *")
        .append(System.identityHashCode(container))
        .append(close);
      return false;
    }

    return true;
  }
}
