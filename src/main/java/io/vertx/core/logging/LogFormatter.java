/*
 * Copyright (c) 2009 Red Hat, Inc.
 * -------------------------------------
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

package io.vertx.core.logging;

import java.util.HashMap;
import java.util.Map;

/**
 * Inspired from LogBack and Slf4j
 *
 * @author Patrick Sauts
 */
public class LogFormatter {

  private static final char PARAM_START = '{';
  private static final String PARAM_STR = "{}";
  private static final char ESCAPE_CHAR = '\\';
  private static final String SEPARATOR = ", ";

  /**
   * @param messagePattern
   * @param arg
   * @return
   */
  public static LogTuple format(String messagePattern, Object arg) {
    if (arg instanceof Object[]) return paramsFormat(messagePattern, (Object[]) arg);
    return paramsFormat(messagePattern, new Object[]{arg});
  }

  /**
   * @param messagePattern
   * @param arg1
   * @param arg2
   * @return
   */
  public static LogTuple format(final String messagePattern, Object arg1, Object arg2) {
    return paramsFormat(messagePattern, new Object[]{arg1, arg2});
  }

  /**
   * @param messagePattern
   * @param params
   * @return
   */
  public static LogTuple paramsFormat(final String messagePattern, final Object[] params) {

    if (params == null) {
      return new LogTuple(messagePattern);
    }

    Throwable throwableCandidate = getThrowableCandidate(params);
    int length = params.length;
    if (throwableCandidate != null) --length;

    if (messagePattern == null) {
      return new LogTuple(join(params, length), throwableCandidate);
    }


    int i = 0;
    int j;
    StringBuilder sbuf = new StringBuilder(messagePattern.length() + 50);

    int L;
    for (L = 0; L < length; L++) {

      j = messagePattern.indexOf(PARAM_STR, i);

      if (j == -1) {
        // no more variables
        if (i == 0) { // this is a simple string
          return new LogTuple(messagePattern, throwableCandidate);
        } else { // add the tail string which contains no variables and return
          // the result.
          sbuf.append(messagePattern.substring(i, messagePattern.length()));
          return new LogTuple(sbuf.toString(), throwableCandidate);
        }
      } else {
        if (isEscapedDelimeter(messagePattern, j)) {
          if (!isDoubleEscaped(messagePattern, j)) {
            L--; // DELIM_START was escaped, thus should not be incremented
            sbuf.append(messagePattern.substring(i, j - 1));
            sbuf.append(PARAM_START);
            i = j + 1;
          } else {
            // The escape character preceding the delimiter start is
            // itself escaped: "abc x:\\{}"
            // we have to consume one backward slash
            sbuf.append(messagePattern.substring(i, j - 1));
            deeplyAppendParameter(sbuf, params[L], new HashMap());
            i = j + 2;
          }
        } else {
          // normal case
          sbuf.append(messagePattern.substring(i, j));
          deeplyAppendParameter(sbuf, params[L], new HashMap());
          i = j + 2;
        }
      }
    }
    // append the characters following the last {} pair.
    sbuf.append(messagePattern.substring(i, messagePattern.length()));
    if (L <= params.length - 1) {
      return new LogTuple(sbuf.toString(), throwableCandidate);
    } else {
      return new LogTuple(sbuf.toString());
    }
  }

  private static String join(Object[] array, int endIndex) {
    if (array == null) {
      return null;
    }
    int startIndex = 0;

    // endIndex - startIndex > 0: Len = NofStrings *(len(firstString) + len(separator))
    // (Assuming that all Strings are roughly equally long)
    int bufSize = (endIndex - startIndex);
    if (bufSize <= 0) {
      return "";
    }

    bufSize *= ((array[startIndex] == null ? 16 : array[startIndex].toString().length()) + SEPARATOR.length());

    StringBuilder buf = new StringBuilder(bufSize);

    for (int i = startIndex; i < endIndex; i++) {
      if (i > startIndex) {
        buf.append(SEPARATOR);
      }
      if (array[i] != null) {
        buf.append(array[i]);
      }
    }
    return buf.toString();

  }

  private static boolean isEscapedDelimeter(String messagePattern, int delimeterStartIndex) {

    if (delimeterStartIndex == 0) {
      return false;
    }
    char potentialEscape = messagePattern.charAt(delimeterStartIndex - 1);
    if (potentialEscape == ESCAPE_CHAR) {
      return true;
    } else {
      return false;
    }
  }

  private static boolean isDoubleEscaped(String messagePattern, int delimeterStartIndex) {
    if (delimeterStartIndex >= 2 && messagePattern.charAt(delimeterStartIndex - 2) == ESCAPE_CHAR) {
      return true;
    } else {
      return false;
    }
  }

  // special treatment of array values was suggested by 'lizongbo'
  private static void deeplyAppendParameter(StringBuilder sbuf, Object o, Map seenMap) {
    if (o == null) {
      sbuf.append("null");
      return;
    }
    if (!o.getClass().isArray()) {
      safeObjectAppend(sbuf, o);
    } else {
      // check for primitive array types because they
      // unfortunately cannot be cast to Object[]
      if (o instanceof boolean[]) {
        booleanArrayAppend(sbuf, (boolean[]) o);
      } else if (o instanceof byte[]) {
        byteArrayAppend(sbuf, (byte[]) o);
      } else if (o instanceof char[]) {
        charArrayAppend(sbuf, (char[]) o);
      } else if (o instanceof short[]) {
        shortArrayAppend(sbuf, (short[]) o);
      } else if (o instanceof int[]) {
        intArrayAppend(sbuf, (int[]) o);
      } else if (o instanceof long[]) {
        longArrayAppend(sbuf, (long[]) o);
      } else if (o instanceof float[]) {
        floatArrayAppend(sbuf, (float[]) o);
      } else if (o instanceof double[]) {
        doubleArrayAppend(sbuf, (double[]) o);
      } else {
        objectArrayAppend(sbuf, (Object[]) o, seenMap);
      }
    }
  }

  private static void safeObjectAppend(StringBuilder sbuf, Object o) {
    try {
      String oAsString = o.toString();
      sbuf.append(oAsString);
    } catch (Throwable t) {
      sbuf.append("!! Invocation of toString() failed on object of type [").append(o.getClass().getName()).append("] !!");
    }

  }

  @SuppressWarnings("unchecked")
  private static void objectArrayAppend(StringBuilder sbuf, Object[] a, Map seenMap) {
    sbuf.append('[');
    if (!seenMap.containsKey(a)) {
      seenMap.put(a, null);
      final int len = a.length;
      for (int i = 0; i < len; i++) {
        deeplyAppendParameter(sbuf, a[i], seenMap);
        if (i != len - 1)
          sbuf.append(", ");
      }
      // allow repeats in siblings
      seenMap.remove(a);
    } else {
      sbuf.append("...");
    }
    sbuf.append(']');
  }

  private static void booleanArrayAppend(StringBuilder sbuf, boolean[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static void byteArrayAppend(StringBuilder sbuf, byte[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static void charArrayAppend(StringBuilder sbuf, char[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static void shortArrayAppend(StringBuilder sbuf, short[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static void intArrayAppend(StringBuilder sbuf, int[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static void longArrayAppend(StringBuilder sbuf, long[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static void floatArrayAppend(StringBuilder sbuf, float[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static void doubleArrayAppend(StringBuilder sbuf, double[] a) {
    sbuf.append('[');
    final int len = a.length;
    for (int i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1)
        sbuf.append(", ");
    }
    sbuf.append(']');
  }

  private static Throwable getThrowableCandidate(Object[] params) {
    if (params == null || params.length == 0) {
      return null;
    }

    final Object lastEntry = params[params.length - 1];
    if (lastEntry instanceof Throwable) {
      return (Throwable) lastEntry;
    }
    return null;
  }

  public static class LogTuple {

    private final String message;
    private final Throwable throwable;

    public LogTuple(String message) {
      this(message, null);
    }

    public LogTuple(String message, Throwable throwable) {
      this.message = message;
      this.throwable = throwable;
    }

    public String getMessage() {
      return message;
    }

    public Throwable getThrowable() {
      return throwable;
    }

  }

}
