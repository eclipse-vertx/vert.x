/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.shared;

import org.nodex.java.core.Immutable;
import org.nodex.java.core.buffer.Buffer;

import java.math.BigDecimal;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 15:03
 */
public class SharedUtils {
  public static <T> T checkObject(T obj) {
    if (obj instanceof Immutable ||
        obj instanceof String ||
        obj instanceof Integer ||
        obj instanceof Long ||
        obj instanceof Boolean ||
        obj instanceof Double ||
        obj instanceof Float ||
        obj instanceof Short ||
        obj instanceof Byte ||
        obj instanceof Character ||
        obj instanceof BigDecimal) {
      return obj;
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
      throw new IllegalArgumentException("Invalid type for shared data structure: " + obj.getClass().getName());
    }
  }
}
