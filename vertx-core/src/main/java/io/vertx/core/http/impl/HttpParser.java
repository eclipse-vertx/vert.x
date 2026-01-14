/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import static io.vertx.core.net.impl.Rfc5234Parser.*;

/**
 * Parser for HTTP as defined by <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-common-rules-for-defining-f">RFC 9110
 * HTTP Semantics</a>
 */
public class HttpParser {

  public static int parseQuotedString(String s, int from, int to) {
    if (from + 1 >= to || !isDQUOTE(s.charAt(from++))) {
      return -1;
    }
    while (true) {
      if (from < to && isQDText(s.charAt(from))) {
        from++;
      } else if (isQuotedPair(s, from, to)) {
        from += 2;
      } else {
        break;
      }
    }
    return from < to && isDQUOTE(s.charAt(from++)) ? from : -1;
  }

  public static boolean isQDText(char c) {
    return isHTAB(c) || isSP(c) || c == 0x21 || (0x23 <= c && c <= 0x5B) || (0x5D <= c && c <= 0x7E) || isObsText(c);
  }

  public static boolean isObsText(char c) {
    return 0x80 <= c && c <= 0xFF;
  }

  public static boolean isQuotedPair(String s, int from, int to) {
    if (from + 1 >= to || s.charAt(from++) != '\\') {
      return false;
    }
    char c = s.charAt(from);
    return isHTAB(c) || isSP(c) || isVCHAR(c) || isObsText(c);
  }

  public static int parseToken(String s, int from, int to) {
    if (from >= to || !isTChar(s.charAt(from++))) {
      return -1;
    }
    while (from < to && isTChar(s.charAt(from))) {
      from++;
    }
    return from;
  }

  public static boolean isTChar(char c) {
    return c == '!' || c == '#' || c == '$' || c == '%' || c == '&' || c == '\'' || c == '*' ||
      c == '+' || c == '-' || c == '.' || c == '^' || c == '_' || c == '`' || c == '|' || c == '~' || isDIGIT(c) || isALPHA(c);
  }

  public static int parseOWS(String s, int from, int to) {
    if (from > to) {
      throw new IllegalArgumentException();
    }
    while (from < to && (isSP(s.charAt(from)) || isHTAB(s.charAt(from)))) {
      from++;
    }
    return from;
  }
}
