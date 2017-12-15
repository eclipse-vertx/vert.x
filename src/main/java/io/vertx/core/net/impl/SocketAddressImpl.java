/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.vertx.core.impl.Arguments;
import io.vertx.core.net.SocketAddress;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketAddressImpl implements SocketAddress{

  private final String hostAddress;
  private final int port;
  private final String path;

  public SocketAddressImpl(InetSocketAddress address) {
    this(address.getPort(), address.getAddress().getHostAddress());
  }

  public SocketAddressImpl(int port, String host) {
    Objects.requireNonNull(host, "no null host accepted");
    Arguments.require(!host.isEmpty(), "no empty host accepted");
    Arguments.requireInRange(port, 0, 65535, "port p must be in range 0 <= p <= 65535");
    this.port = port;
    this.hostAddress = host;
    this.path = null;
  }

  public SocketAddressImpl(String path) {
    this.port = -1;
    this.hostAddress = null;
    this.path = path;
  }

  @Override
  public String path() {
    return path;
  }

  public String host() {
    return hostAddress;
  }

  public int port() {
    return port;
  }

  public String toString() {
    if (path == null) {
      return hostAddress + ":" + port;
    } else {
      return path;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SocketAddressImpl that = (SocketAddressImpl) o;

    if (port != that.port) return false;
    if (hostAddress != null ? !hostAddress.equals(that.hostAddress) : that.hostAddress != null) return false;
    if (path != null ? !path.equals(that.path) : that.path != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = hostAddress != null ? hostAddress.hashCode() : 0;
    result = 31 * result + (path != null ? path.hashCode() : 0);
    result = 31 * result + port;
    return result;
  }
}
