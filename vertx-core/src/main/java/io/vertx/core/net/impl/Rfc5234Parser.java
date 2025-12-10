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

/**
 * Various parsing methods as defined by <a href="https://datatracker.ietf.org/doc/html/rfc5234#appendix-B.1">ABNF</a>
 */
public class Rfc5234Parser {

  // digits lookup table to speed-up parsing
  private static final byte[] DIGITS = new byte[128];

  static {
    Arrays.fill(DIGITS, (byte) -1);
    for (int i = '0';i <= '9';i++) {
      DIGITS[i] = (byte) (i - '0');
    }
  }

  public static boolean isSP(char c) {
    return c == 0x20;
  }

  public static boolean isHTAB(char c) {
    return c == 0x09;
  }

  public static boolean isDQUOTE(char c) {
    return c == '"';
  }

  public static boolean isVCHAR(char c) {
    return 0x21 <= c && c <= 0x7E;
  }

  public static boolean isDIGIT(char ch) {
    if (ch < 128) {
      return DIGITS[ch] != -1;
    }
    return false;
  }

  public static boolean isHEXDIG(char ch) {
    return isDIGIT(ch) || ('A' <= ch && ch <= 'F') || ('a' <= ch && ch <= 'f');
  }

  public static boolean isALPHA(char ch) {
    return ('A' <= ch && ch <= 'Z')
      || ('a'<= ch && ch <= 'z');
  }
}
