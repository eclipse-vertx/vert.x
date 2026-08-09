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

import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.impl.CleanableObject;
import io.vertx.core.internal.CloseableResource;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.lang.ref.Cleaner;

/**
 * A lightweight proxy of Vert.x {@link HttpClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableWebSocketClient extends CleanableObject<WebSocketClient> implements WebSocketClient, MetricsProvider {

  public CleanableWebSocketClient(Cleaner cleaner, CloseableResource<? extends WebSocketClient> resource) {
    super(cleaner, resource);
  }

  @Override
  public ClientWebSocket webSocket() {
    return getOrDie().webSocket();
  }

  public Future<WebSocket> connect(WebSocketConnectOptions options) {
    return getOrDie().connect(options);
  }

  @Override
  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    return getOrDie().updateSSLOptions(options, force);
  }

  @Override
  public Metrics getMetrics() {
    return ((MetricsProvider)getOrDie()).getMetrics();
  }
}
