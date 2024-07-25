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
package io.vertx.core.internal.net;

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public final class URIDecoder {

  private URIDecoder() {
    throw new RuntimeException("Static Class");
  }

  /**
   * Decodes a segment of a URI encoded by a browser.
   *
   * The string is expected to be encoded as per RFC 3986, Section 2. This is the encoding used by JavaScript functions
   * encodeURI and encodeURIComponent, but not escape. For example in this encoding, é (in Unicode U+00E9 or in
   * UTF-8 0xC3 0xA9) is encoded as %C3%A9 or %c3%a9.
   *
   * Plus signs '+' will be handled as spaces and encoded using the default JDK URLEncoder class.
   *
   * @param s string to decode
   *
   * @return decoded string
   */
  public static String decodeURIComponent(String s) {
    return decodeURIComponent(s, true);
  }

  private static int indexOfPercentOrPlus(String s) {
    for (int i = 0, size = s.length(); i < size; i++) {
      final char c = s.charAt(i);
      if (c == '%' || c == '+') {
        return i;
      }
    }
    return -1;
  }

  /**
   * Decodes a segment of an URI encoded by a browser.
   *
   * The string is expected to be encoded as per RFC 3986, Section 2. This is the encoding used by JavaScript functions
   * encodeURI and encodeURIComponent, but not escape. For example in this encoding, é (in Unicode U+00E9 or in
   * UTF-8 0xC3 0xA9) is encoded as %C3%A9 or %c3%a9.
   *
   * @param s string to decode
   * @param plus weather or not to transform plus signs into spaces
   *
   * @return decoded string
   */
  public static String decodeURIComponent(String s, boolean plus) {
    if (s == null) {
      return null;
    }
    int i = !plus ? s.indexOf('%') : indexOfPercentOrPlus(s);
    if (i == -1) {
      return s;
    }
    // pack the slowest path away
    return decodeAndTransformURIComponent(s, i, plus);
  }

  private static String decodeAndTransformURIComponent(String s, int i, boolean plus) {
    final byte[] buf = s.getBytes(StandardCharsets.UTF_8);
    int pos = i;  // position in `buf'.
    for (int size = s.length(); i < size; i++) {
      char c = s.charAt(i);
      if (c == '%') {
        if (i == size - 1) {
          throw new IllegalArgumentException("unterminated escape"
            + " sequence at end of string: " + s);
        }
        c = s.charAt(++i);
        if (c == '%') {
          buf[pos++] = '%';  // "%%" -> "%"
          break;
        }
        if (i >= size - 1) {
          throw new IllegalArgumentException("partial escape"
            + " sequence at end of string: " + s);
        }
        c = decodeHexNibble(c);
        final char c2 = decodeHexNibble(s.charAt(++i));
        if (c == Character.MAX_VALUE || c2 == Character.MAX_VALUE) {
          throw new IllegalArgumentException(
            "invalid escape sequence `%" + s.charAt(i - 1)
              + s.charAt(i) + "' at index " + (i - 2)
              + " of: " + s);
        }
        c = (char) (c * 16 + c2);
        // shouldn't check for plus since it would be a double decoding
        buf[pos++] = (byte) c;
      } else {
        buf[pos++] = (byte) (plus && c == '+' ? ' ' : c);
      }
    }
    return new String(buf, 0, pos, StandardCharsets.UTF_8);
  }

  /**
   * Helper to decode half of a hexadecimal number from a string.
   * @param c The ASCII character of the hexadecimal number to decode.
   * Must be in the range {@code [0-9a-fA-F]}.
   * @return The hexadecimal value represented in the ASCII character
   * given, or {@link Character#MAX_VALUE} if the character is invalid.
   */
  private static char decodeHexNibble(final char c) {
    if ('0' <= c && c <= '9') {
      return (char) (c - '0');
    } else if ('a' <= c && c <= 'f') {
      return (char) (c - 'a' + 10);
    } else if ('A' <= c && c <= 'F') {
      return (char) (c - 'A' + 10);
    } else {
      return Character.MAX_VALUE;
    }
  }
}
