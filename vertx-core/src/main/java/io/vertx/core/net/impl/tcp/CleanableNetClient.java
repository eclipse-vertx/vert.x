/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.impl.CleanableObject;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;

import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * A lightweight proxy of Vert.x {@link NetClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableNetClient extends CleanableObject implements NetClientInternal {

  private final NetClientInternal client;

  public CleanableNetClient(NetClientInternal client, Cleaner cleaner, Consumer<Duration> dispose) {
    super(cleaner, dispose);
    this.client = client;
  }

  public NetClientInternal unwrap() {
    return client;
  }

  @Override
  public Future<NetSocket> connect(int port, String host) {
    return client.connect(port, host);
  }

  @Override
  public Future<NetSocket> connect(int port, String host, String serverName) {
    return client.connect(port, host, serverName);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress) {
    return client.connect(remoteAddress);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress, String serverName) {
    return client.connect(remoteAddress, serverName);
  }

  @Override
  public Future<NetSocket> connect(ConnectOptions connectOptions) {
    return client.connect(connectOptions);
  }

  @Override
  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    return client.updateSSLOptions(options, force);
  }

  @Override
  public void close(Completable<Void> completion) {
    client.close(completion);
  }

  @Override
  public void connectInternal(ConnectOptions connectOptions, Promise<NetSocket> connectHandler, ContextInternal context) {
    client.connectInternal(connectOptions, connectHandler, context);
  }

  @Override
  public Future<Void> closeFuture() {
    return client.closeFuture();
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    clean(timeout);
    return client.closeFuture();
  }

  @Override
  public Metrics getMetrics() {
    return client.getMetrics();
  }
}
