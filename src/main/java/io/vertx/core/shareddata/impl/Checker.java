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

package io.vertx.core.shareddata.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Shareable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Checker {

  static void checkType(Object obj) {
    if (obj instanceof String ||
        obj instanceof Integer ||
        obj instanceof Long ||
        obj instanceof Boolean ||
        obj instanceof Double ||
        obj instanceof Float ||
        obj instanceof Short ||
        obj instanceof Byte ||
        obj instanceof Character ||
        obj instanceof byte[] ||
        obj instanceof Shareable) {
    } else {
      throw new IllegalArgumentException("Invalid type for shareddata data structure: " + obj.getClass().getName());
    }
  }

  static <T> T copyIfRequired(T obj) {
    if (obj instanceof byte[]) {
      //Copy it
      byte[] bytes = (byte[]) obj;
      byte[] copy = new byte[bytes.length];
      System.arraycopy(bytes, 0, copy, 0, bytes.length);
      return (T) copy;
    } else if (obj instanceof Shareable) {
      return (T) ((Shareable) obj).copy();
    } else {
      return obj;
    }
  }

}
