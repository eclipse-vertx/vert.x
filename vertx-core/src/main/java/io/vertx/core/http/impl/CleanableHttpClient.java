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

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpClientConnection;
import io.vertx.core.impl.CleanableObject;
import io.vertx.core.impl.CleanableResource;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientTransport;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.spi.metrics.Metrics;

import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.function.Function;

/**
 * A lightweight proxy of Vert.x {@link HttpClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableHttpClient extends CleanableObject<HttpClientInternal> implements HttpClientInternal {

  public CleanableHttpClient(Cleaner cleaner, CleanableResource<HttpClientInternal> dispose) {
    super(cleaner, dispose);
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions options) {
    return getOrDie().request(options);
  }

  @Override
  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    return getOrDie().updateSSLOptions(options, force);
  }

  @Override
  public VertxInternal vertx() {
    return getOrDie().vertx();
  }

  @Override
  public boolean isMetricsEnabled() {
    return getOrDie().isMetricsEnabled();
  }

  @Override
  public Metrics getMetrics() {
    return getOrDie().getMetrics();
  }

  @Override
  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return getOrDie().redirectHandler();
  }

  @Override
  public HttpClientTransport tcpTransport() {
    return getOrDie().tcpTransport();
  }

  @Override
  public HttpClientTransport quicTransport() {
    return getOrDie().quicTransport();
  }

  @Override
  public HttpClientInternal exceptionHandler(Handler<Throwable> handler) {
    return getOrDie().exceptionHandler(handler);
  }

  @Override
  public HttpClientOptions options() {
    return getOrDie().options();
  }

  @Override
  public HttpClientConfig config() {
    return getOrDie().config();
  }

  @Override
  public Future<Void> closeFuture() {
    return getOrDie().closeFuture();
  }

  @Override
  public void close(Completable<Void> completion) {
    getOrDie().close(completion);
  }

  @Override
  public Future<HttpClientConnection> connect(HttpConnectOptions options) {
    return getOrDie().connect(options);
  }

  @Override
  public EndpointResolverInternal originResolver() {
    return getOrDie().originResolver();
  }

  @Override
  public EndpointResolverInternal resolver() {
    return getOrDie().resolver();
  }

  @Override
  public HttpClientInternal unwrap() {
    return get();
  }
}
