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

package io.vertx.core.metrics.impl;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.metrics.spi.DatagramMetrics;
import io.vertx.core.metrics.spi.EventBusMetrics;
import io.vertx.core.metrics.spi.HttpClientMetrics;
import io.vertx.core.metrics.spi.HttpServerMetrics;
import io.vertx.core.metrics.spi.Metrics;
import io.vertx.core.metrics.spi.NetMetrics;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class MetricsImpl extends AbstractMetrics implements Metrics {

  static final String BASE_NAME = "io.vertx";

  private Counter timers;
  private Counter verticles;

  public MetricsImpl(Vertx vertx, VertxOptions vertxOptions) {
    super(createRegistry(vertxOptions), instanceName(BASE_NAME, vertx));
    initialize(vertxOptions);
  }

  public void initialize(VertxOptions options) {
    if (!isEnabled()) return;

    timers = counter("timers");

    gauge(options::getEventLoopPoolSize, "event-loop-size");
    gauge(options::getWorkerPoolSize, "worker-pool-size");
    if (options.isClustered()) {
      gauge(options::getClusterHost, "cluster-host");
      gauge(options::getClusterPort, "cluster-port");
    }

    verticles = counter("verticles");
  }

  @Override
  public void verticleDeployed(Verticle verticle) {
    if (!isEnabled()) return;

    verticles.inc();
    counter("verticles", verticleName(verticle)).inc();
  }

  @Override
  public void verticleUndeployed(Verticle verticle) {
    if (!isEnabled()) return;

    verticles.dec();
    counter("verticles", verticleName(verticle)).dec();
  }

  @Override
  public void timerCreated(long id) {
    if (!isEnabled()) return;

    timers.inc();
  }

  @Override
  public void timerEnded(long id, boolean cancelled) {
    if (!isEnabled()) return;

    timers.dec();
  }

  @Override
  public EventBusMetrics register(EventBus eventBus) {
    return new EventBusMetricsImpl(this, "io.vertx.eventbus");
  }

  @Override
  public HttpServerMetrics register(HttpServer server, HttpServerOptions options) {
    return new HttpServerMetricsImpl(this, "io.vertx.http.servers");
  }

  @Override
  public HttpClientMetrics register(HttpClient client, HttpClientOptions options) {
    return new HttpClientMetricsImpl(this, instanceName("io.vertx.http.clients", client), options);
  }

  @Override
  public NetMetrics register(NetServer server, NetServerOptions options) {
    return new NetMetricsImpl(this, "io.vertx.net.servers");
  }

  @Override
  public NetMetrics register(NetClient client, NetClientOptions options) {
    return new NetMetricsImpl(this, instanceName("io.vertx.net.clients", client));
  }

  @Override
  public DatagramMetrics register(DatagramSocket socket, DatagramSocketOptions options) {
    return new DatagramMetricsImpl(this, "io.vertx.datagram");
  }

  private static String verticleName(Verticle verticle) {
    return verticle.getClass().getName();
  }

  private static MetricRegistry createRegistry(VertxOptions options) {
    return options.isMetricsEnabled() ? new MetricRegistry() : null;
  }
}
