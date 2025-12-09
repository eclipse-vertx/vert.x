/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import java.util.Arrays;

/**
 * URI related parsing, as in RFC3986
 */
public class UriParser {

  // digits lookup table to speed-up parsing
  private static final byte[] DIGITS = new byte[128];

  static {
    Arrays.fill(DIGITS, (byte) -1);
    for (int i = '0';i <= '9';i++) {
      DIGITS[i] = (byte) (i - '0');
    }
  }

  /**
   * Try to parse the <i>host</i> rul as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseHost(String s, int from, int to) {
    int pos;
    if ((pos = parseIPLiteral(s, from, to)) != -1) {
      return pos;
    } else if ((pos = parseIPv4Address(s, from, to)) != -1) {
      return pos;
    } else if ((pos = parseRegName(s, from, to)) != -1) {
      return pos;
    }
    return -1;
  }

  /**
   * Try to parse the <i>reg-name</i> rul as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseRegName(String s, int from, int to) {
    while (from < to) {
      char c = s.charAt(from);
      if (isUnreserved(c) || isSubDelims(c)) {
        from++;
      } else if (c == '%' && (from + 2) < to && isHEXDIG(s.charAt(c + 1)) && isHEXDIG(s.charAt(c + 2))) {
        from += 3;
      } else {
        break;
      }
    }
    return from;
  }

  /**
   * Try to parse the <i>IP-literal</i> rule: <code>"[" ( IPv6address / IPvFuture  ) "]"</code> as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseIPLiteral(String s, int from, int to) {
    return from + 2 < to && s.charAt(from) == '[' ? foo(s.indexOf(']', from + 2)) : -1;
  }

  private static int foo(int v) {
    return v == -1 ? -1 : v + 1;
  }

  /**
   * Try to parse the <i>IPv4address</i> rule as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseIPv4Address(String s, int from, int to) {
    for (int i = 0;i < 4;i++) {
      if (i > 0 && from < to && s.charAt(from++) != '.') {
        return -1;
      }
      from = parseDecOctet(s, from, to);
      if (from == -1) {
        return -1;
      }
    }
    // from is the next position to parse: whatever come before is a valid IPv4 address
    if (from == to) {
      // we're done
      return from;
    }
    assert from < to;
    // we have more characters, let's check if it has enough space for a port
    if (from + 1 == s.length()) {
      // just a single character left, we don't care what it is
      return -1;
    }
    // we have more characters
    if (s.charAt(from) != ':') {
      // we need : to start a port
      return -1;
    }
    // we (maybe) have a port - even with a single digit; the ipv4 addr is fine
    return from;
  }

  /**
   * Try to parse the <i>dec-octet</i> rule as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseDecOctet(String s, int from, int to) {
    int val = parseAndEvalDigit(s, from++, to);
    if (val < 0 || val > 9) {
      return -1;
    }
    if (val == 0) {
      return from;
    }
    int n = parseAndEvalDigit(s, from, to);
    if (n != -1) {
      val = val * 10 + n;
      n = parseAndEvalDigit(s, ++from, to);
      if (n != -1) {
        from++;
        val = val * 10 + n;
      }
    }
    if (val < 256) {
      return from;
    }
    return -1;
  }

  /**
   * Try to parse the <i>DIGIT</i> rule as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc2234#section-3.4">Value Range Alternatives</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the parsed value or {@code -1} if parsing did not occur
   */
  public static int parseAndEvalDigit(String s, int from, int to) {
    if (from >= to) {
      return -1;
    }
    char ch = s.charAt(from);
    // a very predictable condition
    if (ch < 128) {
      // negative short values are still positive ints
      return DIGITS[ch];
    }
    return -1;
  }

  private static boolean isUnreserved(char ch) {
    return isALPHA(ch) || isDIGIT(ch) || ch == '-' || ch == '.' || ch == '_' || ch == '~';
  }

  private static boolean isALPHA(char ch) {
    return ('A' <= ch && ch <= 'Z')
      || ('a'<= ch && ch <= 'z');
  }

  private static boolean isDIGIT(char ch) {
    if (ch < 128) {
      return DIGITS[ch] != -1;
    }
    return false;
  }

  private static boolean isSubDelims(char ch) {
    return ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+' || ch == ',' || ch == ';' || ch == '=';
  }

  static boolean isHEXDIG(char ch) {
    return isDIGIT(ch) || ('A' <= ch && ch <= 'F') || ('a' <= ch && ch <= 'f');
  }
}
