/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.tcp;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.NetServerInternal;
import io.vertx.core.internal.net.TcpServerInternal;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;

import java.time.Duration;

public class NetServerImpl implements NetServerInternal {

  private Handler<NetSocket> connectHandler;
  private TcpServerInternal delegate;

  public NetServerImpl(TcpServerInternal delegate) {
    this.delegate = delegate;
  }

  @Override
  public NetServerInternal connectHandler(@Nullable Handler<NetSocket> handler) {
    connectHandler = handler;
    delegate.connectHandler((Handler)handler);
    return this;
  }

  @Override
  public NetServerInternal exceptionHandler(Handler<Throwable> handler) {
    delegate.exceptionHandler(handler);
    return this;
  }

  @Override
  public SslContextProvider sslContextProvider() {
    return delegate.sslContextProvider();
  }

  @Override
  public Future<NetServer> listen(ContextInternal context, SocketAddress localAddress) {
    return delegate.listen(context, localAddress).map(this);
  }

  @Override
  public int sniEntrySize() {
    return delegate.sniEntrySize();
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public Handler<NetSocket> connectHandler() {
    return connectHandler;
  }

  @Override
  public Future<NetServer> listen() {
    return delegate.listen().map(this);
  }

  @Override
  public Future<NetServer> listen(SocketAddress localAddress) {
    return delegate.listen(localAddress).map(this);
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return delegate.shutdown(timeout);
  }

  @Override
  public int actualPort() {
    SocketAddress addr = delegate.bindAddress();
    return addr != null ? addr.port() : -1;
  }

  @Override
  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    return delegate.updateSSLOptions(options, force);
  }

  @Override
  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    return delegate.updateTrafficShapingOptions(options);
  }

  @Override
  public Metrics getMetrics() {
    return delegate.getMetrics();
  }
}
