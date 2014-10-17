/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net.impl;

import io.vertx.core.impl.Arguments;
import io.vertx.core.net.SocketAddress;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketAddressImpl implements SocketAddress{

  private final String hostAddress;
  private final int port;

  public SocketAddressImpl(int port, String host) {
    Objects.requireNonNull(host, "no null host accepted");
    Arguments.require(!host.isEmpty(), "no empty host accepted");
    Arguments.requireInRange(port, 0, 65535, "port p must be in range 0 <= p <= 65535");
    this.port = port;
    this.hostAddress = host;
  }

  public String hostAddress() {
    return hostAddress;
  }

  public int hostPort() {
    return port;
  }

  public String toString() {
    return hostAddress + ":" + port;
  }
}
