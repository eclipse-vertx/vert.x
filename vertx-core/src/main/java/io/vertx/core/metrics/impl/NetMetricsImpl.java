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

package io.vertx.core.metrics.impl;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import io.vertx.core.metrics.spi.NetMetrics;
import io.vertx.core.net.SocketAddress;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.codahale.metrics.MetricRegistry.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class NetMetricsImpl extends AbstractMetrics implements NetMetrics {
  private Counter connections;
  private Timer connectionLifetime;
  private Histogram bytesRead;
  private Histogram bytesWritten;
  private Counter exceptions;
  private Map<SocketAddress, Timer.Context> connectionLifetimes;
  private volatile boolean closed;

  public NetMetricsImpl(AbstractMetrics metrics, String baseName, boolean client) {
    super(metrics.registry(), baseName);
    if (client) {
      initialize();
    }
  }

  protected void initialize() {
    if (!isEnabled()) return;

    this.connections = counter("connections");
    this.connectionLifetime = timer("connection-lifetime");
    this.exceptions = counter("exceptions");
    this.bytesRead = histogram("bytes-read");
    this.bytesWritten = histogram("bytes-written");
    this.connectionLifetimes = new ConcurrentHashMap<>();
  }

  @Override
  public void closed() {
    removeAll();
    this.closed = true;
  }

  @Override
  public void listening(SocketAddress localAddress) {
    if (!isEnabled()) return;

    // Set the base name of the server to include the host:port
    setBaseName(name(baseName(), addressName(localAddress)));

    initialize();
  }

  @Override
  public void connected(SocketAddress remoteAddress) {
    if (!isEnabled()) return;

    // Connection metrics
    connections.inc();
    connectionLifetimes.put(remoteAddress, connectionLifetime.time());

    // Remote address connection metrics
    counter("connections", remoteAddress.hostAddress()).inc();
  }

  @Override
  public void disconnected(SocketAddress remoteAddress) {
    if (!isEnabled() || closed) return;

    connections.dec();
    Timer.Context ctx = connectionLifetimes.remove(remoteAddress);
    if (ctx != null) {
      ctx.stop();
    }

    // Remote address connection metrics
    Counter counter = counter("connections", remoteAddress.hostAddress());
    counter.dec();
    if (counter.getCount() == 0) {
      remove("connections", remoteAddress.hostAddress());
    }
  }

  @Override
  public void bytesRead(SocketAddress remoteAddress, long numberOfBytes) {
    if (!isEnabled()) return;

    bytesRead.update(numberOfBytes);
  }

  @Override
  public void bytesWritten(SocketAddress remoteAddress, long numberOfBytes) {
    if (!isEnabled()) return;

    bytesWritten.update(numberOfBytes);
  }

  @Override
  public void exceptionOccurred(SocketAddress remoteAddress, Throwable t) {
    if (!isEnabled()) return;

    exceptions.inc();
  }

  protected long connections() {
    if (connections == null) return 0;

    return connections.getCount();
  }

  protected static String addressName(SocketAddress address) {
    if (address == null) return null;

    return address.hostAddress() + ":" + address.hostPort();
  }
}
