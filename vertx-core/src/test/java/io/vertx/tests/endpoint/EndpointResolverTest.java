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
package io.vertx.tests.endpoint;

import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakeresolver.*;
import org.junit.Test;

import java.util.List;
import java.util.function.Predicate;

public class EndpointResolverTest extends VertxTestBase {

  private final SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
  private final SocketAddress addr2 = SocketAddress.inetSocketAddress(8081, "localhost");
  private final SocketAddress addr3 = SocketAddress.inetSocketAddress(8082, "localhost");
  private final SocketAddress addr4 = SocketAddress.inetSocketAddress(8083, "localhost");

  private FakeAddressResolver fakeResolver;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    fakeResolver = new FakeAddressResolver();
  }

  @Test
  public void testFiltering() {
    fakeResolver.registerAddress("example.com", List.of(addr1, addr2, addr3, addr4));
    EndpointResolverImpl<FakeState, FakeAddress, FakeServerEndpoint> resolver = new EndpointResolverImpl<>((VertxInternal) vertx, fakeResolver, LoadBalancer.ROUND_ROBIN, 5000);
    Endpoint endpoint = resolver.resolveEndpoint(new FakeAddress("example.com")).await();
    Predicate<ServerEndpoint> even = s -> s.address().port() % 2 == 0;
    Predicate<ServerEndpoint> odd = s -> s.address().port() % 2 == 1;
    assertEquals(addr1, endpoint.selectServer(even).address());
    assertEquals(addr3, endpoint.selectServer(even).address());
    assertEquals(addr1, endpoint.selectServer(even).address());
    assertEquals(addr3, endpoint.selectServer(even).address());
    assertEquals(addr2, endpoint.selectServer(odd).address());
    assertEquals(addr4, endpoint.selectServer(odd).address());
    assertEquals(addr2, endpoint.selectServer(odd).address());
    assertEquals(addr4, endpoint.selectServer(odd).address());
  }

  @Test
  public void testFilterEmpty() {
    SocketAddress addr1 = SocketAddress.inetSocketAddress(8080, "localhost");
    fakeResolver.registerAddress("example.com", List.of(addr1, addr2, addr3, addr4));
    EndpointResolverImpl<FakeState, FakeAddress, FakeServerEndpoint> resolver = new EndpointResolverImpl<>((VertxInternal) vertx, fakeResolver, LoadBalancer.ROUND_ROBIN, 5000);
    Endpoint endpoint = resolver.resolveEndpoint(new FakeAddress("example.com")).await();
    Predicate<ServerEndpoint> none = s -> false;
    assertEquals(null, endpoint.selectServer(none));
  }

  @Test
  public void testRebuild() {
    FakeRegistration registration = fakeResolver.registerAddress("example.com", List.of(addr1));
    EndpointResolverImpl<FakeState, FakeAddress, FakeServerEndpoint> resolver = new EndpointResolverImpl<>((VertxInternal) vertx, fakeResolver, LoadBalancer.ROUND_ROBIN, 5000);
    Endpoint endpoint = resolver.resolveEndpoint(new FakeAddress("example.com")).await();
    Predicate<ServerEndpoint> even = s -> s.address().port() % 2 == 0;
    Predicate<ServerEndpoint> odd = s -> s.address().port() % 2 == 1;
    assertEquals(addr1, endpoint.selectServer(even).address());
    assertEquals(addr1, endpoint.selectServer(even).address());
    assertEquals(null, endpoint.selectServer(odd));
    registration.update(List.of(addr1, addr2, addr3, addr4));
    assertEquals(addr1, endpoint.selectServer(even).address());
    assertEquals(addr3, endpoint.selectServer(even).address());
    assertEquals(addr1, endpoint.selectServer(even).address());
    assertEquals(addr3, endpoint.selectServer(even).address());
    assertEquals(addr2, endpoint.selectServer(odd).address());
    assertEquals(addr4, endpoint.selectServer(odd).address());
    assertEquals(addr2, endpoint.selectServer(odd).address());
    assertEquals(addr4, endpoint.selectServer(odd).address());
  }
}
