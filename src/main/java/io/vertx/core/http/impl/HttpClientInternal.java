/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.impl.NetClientInternal;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.resolver.AddressResolver;

public interface HttpClientInternal extends HttpClient, MetricsProvider, Closeable {

  /**
   * @return the vertx, for use in package related classes only.
   */
  VertxInternal vertx();

  HttpClientOptions options();

  NetClientInternal netClient();

  Future<Void> closeFuture();

  /**
   * Configure the client to use an address resolver.
   *
   * @param addressResolver the address resolver
   */
  void addressResolver(AddressResolver<?, ?, ?> addressResolver);

  Future<HttpClientRequest> request(Address address, HttpMethod method, int port, String host, String requestURI);
}
