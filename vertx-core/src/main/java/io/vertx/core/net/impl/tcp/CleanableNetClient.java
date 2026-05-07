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
import io.vertx.core.impl.CleanableResource;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;

import java.lang.ref.Cleaner;
import java.time.Duration;

/**
 * A lightweight proxy of Vert.x {@link NetClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableNetClient extends CleanableObject<NetClientInternal> implements NetClientInternal {

  public CleanableNetClient(Cleaner cleaner, CleanableResource<NetClientInternal> resource) {
    super(cleaner, resource);
  }

  public NetClientInternal unwrap() {
    return get();
  }

  @Override
  public Future<NetSocket> connect(int port, String host) {
    return getOrDie().connect(port, host);
  }

  @Override
  public Future<NetSocket> connect(int port, String host, String serverName) {
    return getOrDie().connect(port, host, serverName);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress) {
    return getOrDie().connect(remoteAddress);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress, String serverName) {
    return getOrDie().connect(remoteAddress, serverName);
  }

  @Override
  public Future<NetSocket> connect(ConnectOptions connectOptions) {
    return getOrDie().connect(connectOptions);
  }

  @Override
  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    return getOrDie().updateSSLOptions(options, force);
  }

  @Override
  public void close(Completable<Void> completion) {
    getOrDie().close(completion);
  }

  @Override
  public void connectInternal(ConnectOptions connectOptions, Promise<NetSocket> connectHandler, ContextInternal context) {
    getOrDie().connectInternal(connectOptions, connectHandler, context);
  }

  @Override
  public Future<Void> closeFuture() {
    return getOrDie().closeFuture();
  }

  @Override
  public Metrics getMetrics() {
    return getOrDie().getMetrics();
  }
}
