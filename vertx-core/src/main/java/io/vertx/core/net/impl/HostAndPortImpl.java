package io.vertx.core.net.impl;

import java.util.Arrays;

import io.vertx.core.net.HostAndPort;

public class HostAndPortImpl implements HostAndPort {

  // digits lookup table to speed-up parsing
  private static final byte[] DIGITS = new byte[128];

  static {
    Arrays.fill(DIGITS, (byte) -1);
    for (int i = '0';i <= '9';i++) {
      DIGITS[i] = (byte) (i - '0');
    }
  }

  public static int parseHost(String val, int from, int to) {
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
    // we (maybe) have a port - even with a single digit; the ipv4 addr is fineFi
    return from;
  }

  public static int parseDecOctet(String s, int from, int to) {
    int val = parseDigit(s, from++, to);
    if (val == 0) {
      return from;
    }
    if (val < 0 || val > 9) {
      return -1;
    }
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
    return -1;
  }

  private static int parseDigit(String s, int from, int to) {
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
    return DIGITS[ch] != -1;
  }

  private static boolean isSubDelims(char ch) {
    return ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+' || ch == ',' || ch == ';' || ch == '=';
  }

  static boolean isHEXDIG(char ch) {
    return isDIGIT(ch) || ('A' <= ch && ch <= 'F') || ('a' <= ch && ch <= 'f');
  }

  /**
   * Validate an authority HTTP header, that is <i>host [':' port]</i> <br>
   * This method should behave like {@link #parseAuthority(String, int)},
   * but without the overhead of creating an object: when {@code true}
   * {@code parseAuthority(s, -1)} should return a non-null value.
   *
   * @param s the string to parse
   * @return {@code true} when the string is a valid authority
   * @throws NullPointerException when the string is {@code null}
   */
  public static boolean isValidAuthority(String s) {
    int pos = parseHost(s, 0, s.length());
    if (pos == s.length()) {
      return true;
    }
    if (pos < s.length() && s.charAt(pos) == ':') {
      return parsePort(s, pos) != -1;
    }
    return false;
  }

  /**
   * Parse an authority HTTP header, that is <i>host [':' port]</i>
   * @param s the string to parse
   * @param schemePort the scheme port used when the optional port is not specified
   * @return the parsed value or {@code null} when the string cannot be parsed
   */
  public static HostAndPortImpl parseAuthority(String s, int schemePort) {
    int pos = parseHost(s, 0, s.length());
    if (pos == s.length()) {
      return new HostAndPortImpl(s, schemePort);
    }
    if (pos < s.length() && s.charAt(pos) == ':') {
      String host = s.substring(0, pos);
      int port = parsePort(s, pos);
      if (port == -1) {
        return null;
      }
      return new HostAndPortImpl(host, port);
    }
    return null;
  }

  private static int parsePort(String s, int pos) {
    int port = 0;
    while (++pos < s.length()) {
      int digit = parseDigit(s, pos, s.length());
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
  public String toString(boolean ssl) {
    if ((port == 80 && !ssl) || (port == 443 && ssl) || port < 0) {
      return host;
    } else {
      return host + ':' + port;
    }
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
