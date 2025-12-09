package io.vertx.core.net.impl;

import io.vertx.core.net.HostAndPort;

public class HostAndPortImpl implements HostAndPort {

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
    int pos = UriParser.parseHost(s, 0, s.length());
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
    int pos = UriParser.parseHost(s, 0, s.length());
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
      int digit = UriParser.parseAndEvalDigit(s, pos, s.length());
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
