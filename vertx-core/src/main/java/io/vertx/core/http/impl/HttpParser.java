package io.vertx.core.http.impl;

import static io.vertx.core.net.impl.Rfc5234Parser.*;

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
