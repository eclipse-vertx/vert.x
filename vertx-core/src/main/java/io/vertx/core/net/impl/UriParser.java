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
package io.vertx.core.net.impl;

import java.util.Arrays;

import static io.vertx.core.net.impl.Rfc5234Parser.*;

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
    if (from < to && s.charAt(from++) == '[') {
      int idx = parseIPv6Address(s, from, to);
      if (idx == -1) {
        idx = parseIPvFuture(s, from, to);
      }
      if (idx != -1 && idx < to && s.charAt(idx++) == ']') {
        return idx;
      }
    }
    return -1;
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
   * Try to parse the <i>IPv6address</i> rule as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseIPv6Address(String s, int from, int to) {
    int doubleColonIdx = s.indexOf("::", from);
    if (doubleColonIdx == -1 || (doubleColonIdx + 2) > to) {
      // Rule1
      for (int i = 0;i < 6;i++) {
        from = parseH16(s, from, to);
        if (from == -1) {
          return -1;
        }
        from = _parseColon(s, from, to);
        if (from == -1) {
          return -1;
        }
      }
      return parseLs32(s, from, to);
    }

    // To ease parsing we use the first colon (:) or double colon (::)
    // e.g. "0::" = "0:" + ":"
    //      "0:1:2:3::" = "0:1:2:3:" + ":"
    int a = doubleColonIdx + 1;
    int count1 = 0;
    while (true) {
      from = parseH16(s, from, a);
      if (from == -1) {
        break;
      }
      from = _parseColon(s, from, a);
      if (from == -1) {
        return -1;
      }
      count1++;
    }

    int count2 = 0;
    from = doubleColonIdx + 2;
    while (true) {
      int idx = parseH16(s, from, to);
      if (idx == -1) {
        break;
      }
      idx = _parseColon(s, idx, to);
      if (idx == -1) {
        break;
      }
      from = idx;
      count2++;
    }
    int idx;
    if ((idx = parseIPv4Address(s, from, to)) != -1) {
      //
    } else if ((idx = parseH16(s, from, to)) != -1) {
      count2--;
    } else {
      // Rule 9
      return count1 <= 7 ? from : -1;
    }
    from = idx;


    switch (count2) {
      case 5:
        // Rule 2
        return count1 == 0 ? from : -1;
      case 4:
        // Rule 3
        return count1 <= 1 ? from : -1;
      case 3:
        // Rule 4
        return count1 <= 2 ? from : -1;
      case 2:
        // Rule 5
        return count1 <= 3 ? from : -1;
      case 1:
        // Rule 6
        return count1 <= 4 ? from : -1;
      case 0:
        // Rule 7
        return count1 <= 5 ? from : -1;
      case -1:
        // Rule 8
        return count1 <= 6 ? from : -1;
      default:
        throw new AssertionError();
    }
  }

  public static int _parseColon(String s, int from, int to) {
    return from < to && s.charAt(from) == ':' ? from + 1 : -1;
  }

  public static int _parseDoubleColon(String s, int from, int to) {
    return from + 1 < to && s.charAt(from) == ':' && s.charAt(from + 1) == ':' ? from + 2 : -1;
  }

  /**
   * Try to parse the <i>ls32</i> rule as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseLs32(String s, int from, int to) {
    int idx = _parseH16ColonH16(s, from, to);
    if (idx == -1) {
      idx = parseIPv4Address(s, from, to);
    }
    return idx;
  }

  public static int _parseH16ColonH16(String s, int from, int to) {
    int idx = parseH16(s, from, to);
    if (idx == -1) {
      return -1;
    }
    from = idx;
    if (from >= to || s.charAt(from++) != ':') {
      return -1;
    }
    return parseH16(s, from, to);
  }

  /**
   * Try to parse the <i>h16</i> rule as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseH16(String s, int from, int to) {
    if (from >= to || !isHEXDIG(s.charAt(from++))) {
      return -1;
    }
    for (int i = 0;i < 3;i++) {
      if (from < to && isHEXDIG(s.charAt(from))) {
        from++;
      }
    }
    return from;
  }

  /**
   * Try to parse the <i>IPvFuture</i> rule as per
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">RFC3986</a>.
   *
   * @param s a string containing the value to parse
   * @param from the index at which parsing begins
   * @param to the index at which parsing ends
   * @return the index after parsing or {@code -1} if the value is invalid
   */
  public static int parseIPvFuture(String s, int from, int to) {
    if (from >= to || s.charAt(from++) != 'v') {
      return -1;
    }
    if (from >= to || !isHEXDIG(s.charAt(from++))) {
      return -1;
    }
    while (from < to) {
      if (isHEXDIG(s.charAt(from))) {
        // Continue
        from++;
      } else {
        break;
      }
    }
    if (from >= to || s.charAt(from++) != '.') {
      return -1;
    }
    int count = 0;
    char c;
    while ((from + count) < to && (isUnreserved(c = s.charAt(from + count)) || isSubDelims(c) || c == ':')) {
      count++;
    }
    if (count == 0) {
      return -1;
    }
    return from + count;
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

  private static boolean isSubDelims(char ch) {
    return ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+' || ch == ',' || ch == ';' || ch == '=';
  }

  public static int parseAndEvalPort(String s, int pos) {
    int port = 0;
    while (++pos < s.length()) {
      int digit = parseAndEvalDigit(s, pos, s.length());
      if (digit == -1) {
        return -1;
      }
      port = port * 10 + digit;
      if (port > 65535) {
        return -1;
      }
    }
    return port;
  }
}
