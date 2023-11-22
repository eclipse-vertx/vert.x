/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author <a href="mailto:aoingl@gmail.com">Lin Gao</a>
 */
public class EndPointKeyTest {

  @Test
  public void testEndPointKey() {
    final SocketAddress addr = SocketAddress.inetSocketAddress(8080, "localhost");
    final HostAndPort peer = HostAndPort.create("localhost", 8080);
    EndpointKey key1 = new EndpointKey(false, null, new ProxyOptions(), addr, peer);
    EndpointKey key2 = new EndpointKey(false, null, new ProxyOptions(), addr, peer);
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
    EndpointKey key3 = new EndpointKey(false, null, new ProxyOptions().setUsername("foo").setPassword("bar"), addr, peer);
    EndpointKey key4 = new EndpointKey(false, null, new ProxyOptions().setUsername("foo").setPassword("bar"), addr, peer);
    assertEquals(key3, key4);
    assertEquals(key3.hashCode(), key4.hashCode());
    EndpointKey key5 = new EndpointKey(false, null, new ProxyOptions().setHost("localhost"), addr, peer);
    EndpointKey key6 = new EndpointKey(false, null, new ProxyOptions().setHost("127.0.0.1"), addr, peer);
    assertNotEquals(key5, key6);
    assertNotEquals(key5.hashCode(), key6.hashCode());
    assertNotEquals(key1.hashCode(), key6.hashCode());
  }

}
