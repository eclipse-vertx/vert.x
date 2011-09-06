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
