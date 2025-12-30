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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.Address;
import io.vertx.core.net.HostAndPort;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Origin implements Address {

  public static Origin fromASCII(String s) {
    int idx = s.indexOf("://");
    if (idx == -1) {
      return null;
    }
    String scheme = s.substring(0, idx);
    int schemePort;
    switch (scheme) {
      case "http":
        schemePort = 80;
        break;
      case "https":
        schemePort = 443;
        break;
      default:
        return null;
    }
    HostAndPort hostAndPort = HostAndPort.parseAuthority(s.substring(idx + 3), schemePort);
    if (hostAndPort == null) {
      return null;
    }
    return new Origin(scheme, hostAndPort.host(), hostAndPort.port());
  }

  public final String scheme;
  public final String host;
  public final int port;

  public Origin(String scheme, String host, int port) {
    this.scheme = scheme;
    this.host = host;
    this.port = port;
  }

  /**
   * ASCII <a href="https://datatracker.ietf.org/doc/html/rfc6454#section-6.2">serialization</a> of an origin.
   */
  public Buffer toASCII() {
    Buffer buffer = Buffer.buffer();
    buffer.appendString(scheme);
    buffer.appendString("://");
    buffer.appendString(host, StandardCharsets.US_ASCII.name());
    int defaultPort;
    switch (scheme) {
      case "http":
        defaultPort = 80;
        break;
      case "https":
        defaultPort = 443;
        break;
      default:
        defaultPort = -1;
        break;
    }
    if (port != defaultPort) {
      buffer.appendByte((byte)':');
      buffer.appendString(Integer.toString(port));
    }
    return buffer;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Origin) {
      Origin that = (Origin) obj;
      return scheme.equals(that.scheme) && host.equals(that.host) && port == that.port;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(scheme, host, port);
  }
}
