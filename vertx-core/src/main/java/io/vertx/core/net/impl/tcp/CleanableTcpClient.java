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
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.net.TcpClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;

import java.lang.ref.Cleaner;
import java.time.Duration;

/**
 * A lightweight proxy of Vert.x {@link TcpClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableTcpClient implements TcpClientInternal {

  static class Action implements Runnable {
    private final TcpClientInternal client;
    private Duration timeout = Duration.ofSeconds(30);
    private Action(TcpClientInternal client) {
      this.client = client;
    }
    @Override
    public void run() {
      client.shutdown(timeout);
    }
  }

  private final TcpClientInternal client;
  private final Cleaner.Cleanable cleanable;
  private final Action action;

  public CleanableTcpClient(TcpClientInternal client, Cleaner cleaner) {
    this.action = new Action(client);
    this.client = client;
    this.cleanable = cleaner.register(this, action);
  }

  public TcpClientInternal unwrap() {
    return client;
  }

  @Override
  public Future<TcpSocket> connect(int port, String host) {
    return client.connect(port, host);
  }

  @Override
  public Future<TcpSocket> connect(int port, String host, String serverName) {
    return client.connect(port, host, serverName);
  }

  @Override
  public Future<TcpSocket> connect(SocketAddress remoteAddress) {
    return client.connect(remoteAddress);
  }

  @Override
  public Future<TcpSocket> connect(SocketAddress remoteAddress, String serverName) {
    return client.connect(remoteAddress, serverName);
  }

  @Override
  public Future<TcpSocket> connect(ConnectOptions connectOptions) {
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
  public void connectInternal(ConnectOptions connectOptions, Promise<TcpSocket> connectHandler, ContextInternal context) {
    client.connectInternal(connectOptions, connectHandler, context);
  }

  @Override
  public Future<Void> closeFuture() {
    return client.closeFuture();
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("Invalid timeout: " + timeout);
    }
    action.timeout = timeout;
    cleanable.clean();
    return client.closeFuture();
  }

  @Override
  public Metrics getMetrics() {
    return client.getMetrics();
  }
}
