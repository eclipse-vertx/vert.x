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
        obj instanceof Buffer ||
        obj instanceof JsonObject ||
        obj instanceof JsonArray ||
        obj instanceof Shareable) {
    } else {
      throw new IllegalArgumentException("Invalid type for shareddata data structure: " + obj.getClass().getName());
    }
  }

  static <T> T copyIfRequired(T obj) {
    if (obj instanceof JsonObject) {
      return (T)((JsonObject)obj).copy();
    } else if (obj instanceof JsonArray) {
      return (T) ((JsonArray) obj).copy();
    } else if (obj instanceof byte[]) {
      //Copy it
      byte[] bytes = (byte[]) obj;
      byte[] copy = new byte[bytes.length];
      System.arraycopy(bytes, 0, copy, 0, bytes.length);
      return (T) copy;
    } else if (obj instanceof Buffer) {
      //Copy it
      return (T) ((Buffer) obj).copy();
    } else {
      return obj;
    }
  }

}
