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

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.internal.net.TcpClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;

import java.time.Duration;

public class NetClientImpl implements NetClientInternal {

  private TcpClientInternal delegate;

  public NetClientImpl(TcpClientInternal delegate) {
    this.delegate = delegate;
  }

  @Override
  public void connectInternal(ConnectOptions connectOptions, Promise<NetSocket> connectHandler, ContextInternal context) {
    delegate.connectInternal(connectOptions, (Promise)connectHandler, context);
  }

  @Override
  public Future<Void> closeFuture() {
    return delegate.closeFuture();
  }

  @Override
  public Future<NetSocket> connect(int port, String host) {
    return (Future)delegate.connect(port, host);
  }

  @Override
  public Future<NetSocket> connect(int port, String host, String serverName) {
    return (Future)delegate.connect(port, host, serverName);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress) {
    return (Future)delegate.connect(remoteAddress);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress, String serverName) {
    return (Future)delegate.connect(remoteAddress, serverName);
  }

  @Override
  public Future<NetSocket> connect(ConnectOptions connectOptions) {
    return (Future)delegate.connect(connectOptions);
  }

  @Override
  public void close(Completable<Void> completion) {
    delegate.close(completion);
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return delegate.shutdown(timeout);
  }

  @Override
  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    return delegate.updateSSLOptions(options, force);
  }

  @Override
  public Metrics getMetrics() {
    return delegate.getMetrics();
  }
}
