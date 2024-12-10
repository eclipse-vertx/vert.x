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
import java.util.Objects;

/**
 * Various RFC3986 util methods.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public final class RFC3986 {

  private RFC3986() {
  }

  /**
   * Decodes a segment of a URI encoded by a browser.
   *
   * <p>The string is expected to be encoded as per RFC 3986, Section 2. This is the encoding used by JavaScript functions
   * encodeURI and encodeURIComponent, but not escape. For example in this encoding, é (in Unicode U+00E9 or in
   * UTF-8 0xC3 0xA9) is encoded as %C3%A9 or %c3%a9.
   *
   * <p>Plus signs '+' will be handled as spaces and encoded using the default JDK URLEncoder class.
   *
   * @param s string to decode
   * @return decoded string
   */
  public static String decodeURIComponent(String s) {
    return decodeURIComponent(s, true);
  }

  /**
   * Decodes a segment of an URI encoded by a browser.
   *
   * <p>The string is expected to be encoded as per RFC 3986, Section 2. This is the encoding used by JavaScript functions
   * encodeURI and encodeURIComponent, but not escape. For example in this encoding, é (in Unicode U+00E9 or in
   * UTF-8 0xC3 0xA9) is encoded as %C3%A9 or %c3%a9.
   *
   * @param s string to decode
   * @param plus whether to convert plus char into spaces
   *
   * @return decoded string
   */
  public static String decodeURIComponent(String s, boolean plus) {
    Objects.requireNonNull(s);
    int i = !plus ? s.indexOf('%') : indexOfPercentOrPlus(s);
    if (i == -1) {
      return s;
    }
    // pack the slowest path away
    return decodeAndTransformURIComponent(s, i, plus);
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

  /**
   * Removed dots as per <a href="http://tools.ietf.org/html/rfc3986#section-5.2.4">rfc3986</a>.
   *
   * <p>There is 1 extra transformation that are not part of the spec but kept for backwards compatibility:
   * double slash // will be converted to single slash.
   *
   * @param path raw path
   * @return normalized path
   */
  public static String removeDotSegments(CharSequence path) {
    Objects.requireNonNull(path);
    final StringBuilder obuf = new StringBuilder(path.length());
    int i = 0;
    while (i < path.length()) {
      // remove dots as described in
      // http://tools.ietf.org/html/rfc3986#section-5.2.4
      if (matches(path, i, "./")) {
        i += 2;
      } else if (matches(path, i, "../")) {
        i += 3;
      } else if (matches(path, i, "/./")) {
        // preserve last slash
        i += 2;
      } else if (matches(path, i,"/.", true)) {
        path = "/";
        i = 0;
      } else if (matches(path, i, "/../")) {
        // preserve last slash
        i += 3;
        int pos = obuf.lastIndexOf("/");
        if (pos != -1) {
          obuf.delete(pos, obuf.length());
        }
      } else if (matches(path, i, "/..", true)) {
        path = "/";
        i = 0;
        int pos = obuf.lastIndexOf("/");
        if (pos != -1) {
          obuf.delete(pos, obuf.length());
        }
      } else if (matches(path, i, ".", true) || matches(path, i, "..", true)) {
        break;
      } else {
        if (path.charAt(i) == '/') {
          i++;
          // Not standard!!!
          // but common // -> /
          if (obuf.length() == 0 || obuf.charAt(obuf.length() - 1) != '/') {
            obuf.append('/');
          }
        }
        int pos = indexOfSlash(path, i);
        if (pos != -1) {
          obuf.append(path, i, pos);
          i = pos;
        } else {
          obuf.append(path, i, path.length());
          break;
        }
      }
    }
    return obuf.toString();
  }

  private static boolean matches(CharSequence path, int start, String what) {
    return matches(path, start, what, false);
  }

  private static boolean matches(CharSequence path, int start, String what, boolean exact) {
    if (exact) {
      if (path.length() - start != what.length()) {
        return false;
      }
    }

    if (path.length() - start >= what.length()) {
      for (int i = 0; i < what.length(); i++) {
        if (path.charAt(start + i) != what.charAt(i)) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  private static int indexOfSlash(CharSequence str, int start) {
    for (int i = start; i < str.length(); i++) {
      if (str.charAt(i) == '/') {
        return i;
      }
    }
    return -1;
  }

  /**
   * Normalizes a path as per <a href="http://tools.ietf.org/html/rfc3986#section-5.2.4>rfc3986</a>.
   *
   * There are 2 extra transformations that are not part of the spec but kept for backwards compatibility:
   *
   * double slash // will be converted to single slash and the path will always start with slash.
   *
   * Null paths are not normalized as nothing can be said about them.
   *
   * @param pathname raw path
   * @return normalized path
   */
  public static String normalizePath(String pathname) {
    // add trailing slash if not set
    if (pathname.isEmpty()) {
      return "/";
    }

    int indexOfFirstPercent = pathname.indexOf('%');
    if (indexOfFirstPercent == -1) {
      // no need to removeDots nor replace double slashes
      if (pathname.indexOf('.') == -1 && pathname.indexOf("//") == -1) {
        if (pathname.charAt(0) == '/') {
          return pathname;
        }
        // See https://bugs.openjdk.org/browse/JDK-8085796
        return "/" + pathname;
      }
    }
    return normalizePathSlow(pathname, indexOfFirstPercent);
  }

  private static String normalizePathSlow(String pathname, int indexOfFirstPercent) {
    final StringBuilder ibuf;
    // Not standard!!!
    if (pathname.charAt(0) != '/') {
      ibuf = new StringBuilder(pathname.length() + 1);
      ibuf.append('/');
      if (indexOfFirstPercent != -1) {
        indexOfFirstPercent++;
      }
    } else {
      ibuf = new StringBuilder(pathname.length());
    }
    ibuf.append(pathname);
    if (indexOfFirstPercent != -1) {
      decodeUnreservedChars(ibuf, indexOfFirstPercent);
    }
    // remove dots as described in
    // http://tools.ietf.org/html/rfc3986#section-5.2.4
    return RFC3986.removeDotSegments(ibuf);
  }

  private static void decodeUnreservedChars(StringBuilder path, int start) {
    while (start < path.length()) {
      // decode unreserved chars described in
      // http://tools.ietf.org/html/rfc3986#section-2.4
      if (path.charAt(start) == '%') {
        decodeUnreserved(path, start);
      }

      start++;
    }
  }

  private static void decodeUnreserved(StringBuilder path, int start) {
    if (start + 3 <= path.length()) {
      // these are latin chars so there is no danger of falling into some special unicode char that requires more
      // than 1 byte
      final String escapeSequence = path.substring(start + 1, start + 3);
      int unescaped;
      try {
        unescaped = Integer.parseInt(escapeSequence, 16);
        if (unescaped < 0) {
          throw new IllegalArgumentException("Invalid escape sequence: %" + escapeSequence);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid escape sequence: %" + escapeSequence);
      }
      // validate if the octet is within the allowed ranges
      if (
        // ALPHA
        (unescaped >= 0x41 && unescaped <= 0x5A) ||
          (unescaped >= 0x61 && unescaped <= 0x7A) ||
          // DIGIT
          (unescaped >= 0x30 && unescaped <= 0x39) ||
          // HYPHEN
          (unescaped == 0x2D) ||
          // PERIOD
          (unescaped == 0x2E) ||
          // UNDERSCORE
          (unescaped == 0x5F) ||
          // TILDE
          (unescaped == 0x7E)) {

        path.setCharAt(start, (char) unescaped);
        path.delete(start + 1, start + 3);
      }
    } else {
      throw new IllegalArgumentException("Invalid position for escape character: " + start);
    }
  }
}
