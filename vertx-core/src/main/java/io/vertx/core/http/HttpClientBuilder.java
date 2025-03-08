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
package io.vertx.core.http;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;

import java.util.function.Function;

/**
 * A builder for {@link HttpClient}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpClientBuilder {

  /**
   * Configure the client options.
   * @param options the client options
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientBuilder with(HttpClientOptions options);

  /**
   * Configure the client with the given pool {@code options}.
   * @param options the pool options
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientBuilder with(PoolOptions options);

  /**
   * Set a connection handler for the client. This handler is called when a new connection is established.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientBuilder withConnectHandler(Handler<HttpConnection> handler);

  /**
   * Set a redirect handler for the http client.
   * <p>
   * The redirect handler is called when a {@code 3xx} response is received and the request is configured to
   * follow redirects with {@link HttpClientRequest#setFollowRedirects(boolean)}.
   * <p>
   * The redirect handler is passed the {@link HttpClientResponse}, it can return an {@link HttpClientRequest} or {@code null}.
   * <ul>
   *   <li>when null is returned, the original response is processed by the original request response handler</li>
   *   <li>when a new {@code Future<HttpClientRequest>} is returned, the client will send this new request</li>
   * </ul>
   * The new request will get a copy of the previous request headers unless headers are set. In this case,
   * the client assumes that the redirect handler exclusively managers the headers of the new request.
   * <p>
   * The handler must return a {@code Future<HttpClientRequest>} unsent so the client can further configure it and send it.
   *
   * @param handler the new redirect handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpClientBuilder withRedirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler);

  /**
   * Configure the client to use a specific address resolver.
   *
   * @param resolver the address resolver
   */
  @GenIgnore
  HttpClientBuilder withAddressResolver(AddressResolver<?> resolver);

  /**
   * Configure the client to use a load balancer.
   *
   * @param loadBalancer the load balancer
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  HttpClientBuilder withLoadBalancer(LoadBalancer loadBalancer);

  /**
   * Build and return the client.
   * @return the client as configured by this builder
   */
  HttpClientAgent build();

}
