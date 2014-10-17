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

import io.vertx.core.Verticle;
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

/**
 * There's one instance of this per Vert.x instance
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface VertxMetrics extends BaseMetrics, Measured {

  void verticleDeployed(Verticle verticle);

  void verticleUndeployed(Verticle verticle);

  void timerCreated(long id);

  void timerEnded(long id, boolean cancelled);

  EventBusMetrics createMetrics(EventBus eventBus);

  HttpServerMetrics createMetrics(HttpServer server, HttpServerOptions options);

  HttpClientMetrics createMetrics(HttpClient client, HttpClientOptions options);

  NetMetrics createMetrics(NetServer server, NetServerOptions options);

  NetMetrics createMetrics(NetClient client, NetClientOptions options);

  DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options);
}
