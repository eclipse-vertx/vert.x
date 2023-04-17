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
package io.vertx.core.net.impl;

import io.vertx.core.Future;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;

import java.lang.ref.Cleaner;
import java.util.concurrent.TimeUnit;

/**
 * A lightweight proxy of Vert.x {@link NetClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableNetClient implements NetClient {

  static class Action implements Runnable {
    private final NetClientInternal client;
    private long timeout = 30L;
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    private Action(NetClientInternal client) {
      this.client = client;
    }
    @Override
    public void run() {
      client.close(timeout, timeUnit);
    }
  }

  private final NetClientInternal client;
  private final Cleaner.Cleanable cleanable;
  private final Action action;

  public CleanableNetClient(NetClientInternal client, Cleaner cleaner) {
    this.action = new Action(client);
    this.client = client;
    this.cleanable = cleaner.register(this, action);
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
  public Future<Void> close() {
    action.timeout = 0L;
    action.timeUnit = TimeUnit.SECONDS;
    cleanable.clean();
    return (client).closeFuture();
  }

  @Override
  public Future<Void> updateSSLOptions(SSLOptions options) {
    return client.updateSSLOptions(options);
  }
}
