/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
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
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakeresolver.FakeAddress;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class MappingResolverTest extends VertxTestBase {

  volatile Function<Address, List<SocketAddress>> mapping;
  EndpointResolverInternal endpointResolver;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AddressResolver ar = AddressResolver.mappingResolver(addr -> {
      Function<Address, List<SocketAddress>> m = mapping;
      return m != null ? m.apply(addr) : null;
    });
    endpointResolver = EndpointResolverInternal.create(
      (VertxInternal) vertx, ar.endpointResolver(vertx), LoadBalancer.ROUND_ROBIN, 5000);
  }

  @Test
  public void testLookup() throws Exception {
    FakeAddress lookup = new FakeAddress("svc");
    mapping = addr -> Collections.singletonList(SocketAddress.inetSocketAddress(80, addr.toString()));
    Endpoint endpoint = awaitFuture(endpointResolver.resolveEndpoint(lookup));
    ServerEndpoint node = endpoint.selectServer();
    assertEquals("ServiceName(svc)", node.address().host());
    assertEquals(80, node.address().port());
  }

  @Test
  public void testReturnNull() throws Exception {
    FakeAddress lookup = new FakeAddress("svc");
    mapping = addr -> null;
    Endpoint endpoint = awaitFuture(endpointResolver.resolveEndpoint(lookup));
    try {
      endpoint.selectServer();
      fail();
    } catch (IllegalStateException ignore) {
    }
  }

  @Test
  public void testRevalidation() throws Exception {
    FakeAddress lookup = new FakeAddress("svc");
    AtomicReference<List<SocketAddress>> ref = new AtomicReference<>();
    ref.set(List.of(SocketAddress.inetSocketAddress(80, "addr1")));
    mapping = addr -> ref.get();
    Endpoint endpoint = awaitFuture(endpointResolver.resolveEndpoint(lookup));
    assertEquals("addr1", endpoint.selectServer().address().host());
    assertEquals("addr1", endpoint.selectServer().address().host());
    ref.set(List.of(SocketAddress.inetSocketAddress(80, "addr1"), SocketAddress.inetSocketAddress(80, "addr2")));
    assertEquals("addr1", endpoint.selectServer().address().host());
    assertEquals("addr2", endpoint.selectServer().address().host());
  }
}
