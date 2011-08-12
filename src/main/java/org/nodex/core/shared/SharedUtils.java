package org.nodex.core.shared;

import org.nodex.core.Threadsafe;

import java.math.BigDecimal;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 15:03
 */
public class SharedUtils {
  public static void checkObject(Object obj) {
    if (!(obj instanceof Threadsafe ||
        obj instanceof String ||
        obj instanceof Integer ||
        obj instanceof Long ||
        obj instanceof Boolean ||
        obj instanceof Double ||
        obj instanceof Float ||
        obj instanceof Short ||
        obj instanceof Character ||
        obj instanceof BigDecimal)) {
      throw new IllegalArgumentException("Invalid type for shared data structure: " + obj.getClass().getName());
    }
  }
}
