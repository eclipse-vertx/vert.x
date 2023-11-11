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
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.lang.ref.Cleaner;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * A lightweight proxy of Vert.x {@link HttpClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableWebSocketClient implements WebSocketClient, MetricsProvider, Closeable {

  static class Action implements Runnable {
    private final BiFunction<Long, TimeUnit, Future<Void>> dispose;
    private long timeout = 30L;
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    private Future<Void> closeFuture;
    private Action(BiFunction<Long, TimeUnit, Future<Void>> dispose) {
      this.dispose = dispose;
    }
    @Override
    public void run() {
      closeFuture = dispose.apply(timeout, timeUnit);
    }
  }

  public final WebSocketClient delegate;
  private final Cleaner.Cleanable cleanable;
  private final Action action;

  public CleanableWebSocketClient(WebSocketClient delegate, Cleaner cleaner, BiFunction<Long, TimeUnit, Future<Void>> dispose) {
    this.action = new Action(dispose);
    this.delegate = delegate;
    this.cleanable = cleaner.register(this, action);
  }

  @Override
  public ClientWebSocket webSocket() {
    return delegate.webSocket();
  }

  public Future<WebSocket> connect(WebSocketConnectOptions options) {
    return delegate.connect(options);
  }

  @Override
  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    return delegate.updateSSLOptions(options, force);
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit timeUnit) {
    if (timeout < 0L) {
      throw new IllegalArgumentException();
    }
    if (timeUnit == null) {
      throw new IllegalArgumentException();
    }
    action.timeout = timeout;
    action.timeUnit = timeUnit;
    cleanable.clean();
    return action.closeFuture;
  }

  @Override
  public void close(Promise<Void> completion) {
    ((Closeable)delegate).close(completion);
  }

  @Override
  public Metrics getMetrics() {
    return ((MetricsProvider)delegate).getMetrics();
  }
}
