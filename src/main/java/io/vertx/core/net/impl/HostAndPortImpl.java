package io.vertx.core.net.impl;

import io.vertx.core.net.HostAndPort;

public class HostAndPortImpl implements HostAndPort {

  static int parseHost(String val, int from, int to) {
    int pos;
    if ((pos = parseIPLiteral(val, from, to)) != -1) {
      return pos;
    } else if ((pos = parseIPv4Address(val, from, to)) != -1) {
      return pos;
    } else if ((pos = parseRegName(val, from, to)) != -1) {
      return pos;
    }
    return -1;
  }

  private static int foo(int v) {
    return v == -1 ? -1 : v + 1;
  }

  static int parseIPv4Address(String s, int from, int to) {
    for (int i = 0;i < 4;i++) {
      if (i > 0 && from < to && s.charAt(from++) != '.') {
        return -1;
      }
      from = parseDecOctet(s, from, to);
      if (from == -1) {
        return -1;
      }
    }
    return from < to && (from + 1 == s.length() || s.charAt(from + 1) != ':') ? -1 : from;
  }

  static int parseDecOctet(String s, int from, int to) {
    int val = parseDigit(s, from++, to);
    switch (val) {
      case 0:
        return from;
      case 1:
      case 2:
      case 3:
      case 4:
      case 5:
      case 6:
      case 7:
      case 8:
      case 9:
        int n = parseDigit(s, from, to);
        if (n != -1) {
          val = val * 10 + n;
          n = parseDigit(s, ++from, to);
          if (n != -1) {
            from++;
            val = val * 10 + n;
          }
        }
        if (val < 256) {
          return from;
        }
    }
    return -1;
  }

  private static int parseDigit(String s, int from, int to) {
    char c;
    return from < to && isDIGIT(c = s.charAt(from)) ? c - '0' : -1;
  }

  public static int parseIPLiteral(String s, int from, int to) {
    return from + 2 < to && s.charAt(from) == '[' ? foo(s.indexOf(']', from + 2)) : -1;
  }

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

  private static boolean isUnreserved(char ch) {
    return isALPHA(ch) || isDIGIT(ch) || ch == '-' || ch == '.' || ch == '_' || ch == '~';
  }

  private static boolean isALPHA(char ch) {
    return ('A' <= ch && ch <= 'Z')
      || ('a'<= ch && ch <= 'z');
  }

  private static boolean isDIGIT(char ch) {
    return ('0' <= ch && ch <= '9');
  }

  private static boolean isSubDelims(char ch) {
    return ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+' || ch == ',' || ch == ';' || ch == '=';
  }

  static boolean isHEXDIG(char ch) {
    return isDIGIT(ch) || ('A' <= ch && ch <= 'F') || ('a' <= ch && ch <= 'f');
  }

  /**
   * Parse an authority HTTP header, that is <i>host [':' port]</i>
   * @param s the string to port
   * @param schemePort the scheme port used when the optional port is specified
   * @return the parsed value or {@code null} when the string cannot be parsed
   */
  public static HostAndPortImpl parseHostAndPort(String s, int schemePort) {
    int pos = parseHost(s, 0, s.length());
    if (pos == s.length()) {
      return new HostAndPortImpl(s, schemePort);
    }
    if (pos < s.length() && s.charAt(pos) == ':') {
      String host = s.substring(0, pos);
      int port = 0;
      while (++pos < s.length()) {
        int digit = parseDigit(s, pos, s.length());
        if (digit == -1) {
          return null;
        }
        port = port * 10 + digit;
      }
      return new HostAndPortImpl(host, port);
    }
    return null;
  }

  private final String host;
  private final int port;

  public HostAndPortImpl(String host, int port) {
    if (host == null) {
      throw new NullPointerException();
    }
    this.host = host;
    this.port = port;
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  @Override
  public int hashCode() {
    return host.hashCode() ^ port;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof HostAndPort) {
      HostAndPort that = (HostAndPort) obj;
      return port == that.port() && host.equals(that.host());
    }
    return false;
  }

  @Override
  public String toString() {
    if (port >= 0) {
      return host + ':' + port;
    } else {
      return host;
    }
  }
}
