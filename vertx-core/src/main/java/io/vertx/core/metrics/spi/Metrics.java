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

package io.vertx.core.metrics.spi;

import io.vertx.core.ServiceHelper;
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
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.spi.MetricsFactory;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface Metrics extends BaseMetrics, Measured {

  void verticleDeployed(Verticle verticle);

  void verticleUndeployed(Verticle verticle);

  void timerCreated(long id);

  void timerEnded(long id, boolean cancelled);

  EventBusMetrics register(EventBus eventBus);

  HttpServerMetrics register(HttpServer server, HttpServerOptions options);

  HttpClientMetrics register(HttpClient client, HttpClientOptions options);

  NetMetrics register(NetServer server, NetServerOptions options);

  NetMetrics register(NetClient client, NetClientOptions options);

  DatagramMetrics register(DatagramSocket socket, DatagramSocketOptions options);

  void stop();

  static Metrics metrics(Vertx vertx, VertxOptions options) {
    return factory.metrics(vertx, options);
  }

  static final MetricsFactory factory = ServiceHelper.loadFactory(MetricsFactory.class);
}
