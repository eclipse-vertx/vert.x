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

package io.vertx.core.net.impl;

import io.netty.util.NetUtil;
import io.vertx.core.VertxException;
import io.vertx.core.impl.Arguments;
import io.vertx.core.net.SocketAddress;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketAddressImpl implements SocketAddress{

  private final String host;
  private final String hostName;
  private final InetAddress ipAddress;
  private final int port;
  private final String path;

  public SocketAddressImpl(InetSocketAddress address) {
    Arguments.requireInRange(address.getPort(), 0, 65535, "port p must be in range 0 <= p <= 65535");
    this.path = null;
    this.port= address.getPort();
    this.host = address.getHostString();
    if (address.isUnresolved()) {
      this.hostName = address.getHostName();
      this.ipAddress = null;
    } else {
      String host = address.getHostString();
      if (NetUtil.isValidIpV4Address(host) || NetUtil.isValidIpV6Address(host)) {
        host = null;
      }
      this.hostName = host;
      this.ipAddress = address.getAddress();
    }
  }

  public SocketAddressImpl(int port, String host) {
    Arguments.requireInRange(port, 0, 65535, "port p must be in range 0 <= p <= 65535");
    this.path = null;
    this.port = port;
    if (NetUtil.isValidIpV4Address(host)) {
      InetAddress ip;
      try {
        ip = InetAddress.getByAddress(NetUtil.createByteArrayFromIpAddressString(host));
      } catch (UnknownHostException e) {
        throw new VertxException(e);
      }
      this.host = host;
      this.hostName = null;
      this.ipAddress = ip;
    } else if (NetUtil.isValidIpV6Address(host)) {
      Inet6Address ip = NetUtil.getByName(host);
      this.host = host;
      this.hostName = null;
      this.ipAddress = ip;
    } else {
      Arguments.require(!host.isEmpty(), "host name must not be empty");
      this.host = host;
      this.hostName = host;
      this.ipAddress = null;
    }
  }

  public SocketAddressImpl(String path) {
    Objects.requireNonNull(path, "domain socket path must be non null");
    Arguments.require(!path.isEmpty(), "domain socket must not be empty");
    this.port = -1;
    this.host = null;
    this.ipAddress = null;
    this.hostName = null;
    this.path = path;
  }

  @Override
  public String path() {
    return path;
  }

  public String host() {
    return host;
  }

  @Override
  public String hostName() {
    return hostName;
  }

  @Override
  public String hostAddress() {
    return ipAddress == null ? null : ipAddress.getHostAddress();
  }

  public int port() {
    return port;
  }

  @Override
  public boolean isInetSocket() {
    return path == null;
  }

  @Override
  public boolean isDomainSocket() {
    return path != null;
  }

  public String toString() {
    if (path == null) {
      return host + ":" + port;
    } else {
      return path;
    }
  }

  public InetAddress ipAddress() {
    return ipAddress;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SocketAddressImpl that = (SocketAddressImpl) o;

    if (port != that.port) return false;
    if (host != null ? !host.equals(that.host) : that.host != null) return false;
    if (path != null ? !path.equals(that.path) : that.path != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = host != null ? host.hashCode() : 0;
    result = 31 * result + (path != null ? path.hashCode() : 0);
    result = 31 * result + port;
    return result;
  }
}
