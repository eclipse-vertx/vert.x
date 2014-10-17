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
  private Counter openConnections;
  private Timer connections;
  private Histogram bytesRead;
  private Histogram bytesWritten;
  private Counter exceptions;
  private Map<SocketAddress, Timer.Context> connectionLifetimes;
  volatile boolean closed;

  public NetMetricsImpl(AbstractMetrics metrics, String baseName, boolean client) {
    super(metrics.registry(), baseName);
    if (client) {
      initialize();
    }
  }

  protected void initialize() {
    if (!isEnabled()) return;

    this.openConnections = counter("open-connections");
    this.connections = timer("connections");
    this.exceptions = counter("exceptions");
    this.bytesRead = histogram("bytes-read");
    this.bytesWritten = histogram("bytes-written");
    this.connectionLifetimes = new ConcurrentHashMap<>();
  }

  @Override
  public void closed() {
    this.closed = true;
    removeAll();
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
    if (!isEnabled() || closed) return;

    // Connection metrics
    openConnections.inc();
    connectionLifetimes.put(remoteAddress, connections.time());

    // Remote address connection metrics
    counter("open-connections", remoteAddress.hostAddress()).inc();

    // A little clunky, but it's possible we got here after closed has been called
    if (closed) {
      removeAll();
    }
  }

  @Override
  public void disconnected(SocketAddress remoteAddress) {
    if (!isEnabled() || closed) return;

    openConnections.dec();
    Timer.Context ctx = connectionLifetimes.remove(remoteAddress);
    if (ctx != null) {
      ctx.stop();
    }

    // Remote address connection metrics
    Counter counter = counter("open-connections", remoteAddress.hostAddress());
    counter.dec();
    if (counter.getCount() == 0) {
      remove("open-connections", remoteAddress.hostAddress());
    }

    // A little clunky, but it's possible we got here after closed has been called
    if (closed) {
      removeAll();
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
    if (openConnections == null) return 0;

    return openConnections.getCount();
  }

  protected static String addressName(SocketAddress address) {
    if (address == null) return null;

    return address.hostAddress() + ":" + address.hostPort();
  }
}
