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
  private String serverName;

  public NetMetricsImpl(AbstractMetrics metrics, String baseName) {
    super(metrics.registry(), baseName);
  }

  @Override
  public void listening(SocketAddress localAddress) {
    if (!isEnabled()) return;

    this.serverName = addressName(localAddress);
    this.connections = counter(serverName, "connections");
    this.connectionLifetime = timer(serverName, "connection-lifetime");
    this.exceptions = counter(serverName, "exceptions");
    this.bytesRead = histogram(serverName, "bytes-read");
    this.bytesWritten = histogram(serverName, "bytes-written");
    this.connectionLifetimes = new ConcurrentHashMap<>();
  }

  @Override
  public void connected(SocketAddress remoteAddress) {
    if (!shouldMeasure(remoteAddress)) return;

    // Connection metrics
    connections.inc();
    connectionLifetimes.put(remoteAddress, connectionLifetime.time());

    // Remote address connection metrics
    counter(serverName, "connections", remoteAddress.hostAddress()).inc();
  }

  @Override
  public void disconnected(SocketAddress remoteAddress) {
    if (!shouldMeasure(remoteAddress)) return;

    connections.dec();
    Timer.Context ctx = connectionLifetimes.remove(remoteAddress);
    if (ctx != null) {
      ctx.stop();
    }

    // Remote address connection metrics
    counter(serverName, "connections", remoteAddress.hostAddress()).dec();
  }

  @Override
  public void bytesRead(SocketAddress remoteAddress, long numberOfBytes) {
    if (!shouldMeasure(remoteAddress)) return;

    bytesRead.update(numberOfBytes);
  }

  @Override
  public void bytesWritten(SocketAddress remoteAddress, long numberOfBytes) {
    if (!shouldMeasure(remoteAddress)) return;

    bytesWritten.update(numberOfBytes);
  }

  @Override
  public void exceptionOccurred(SocketAddress remoteAddress, Throwable t) {
    if (!shouldMeasure(remoteAddress)) return;

    exceptions.inc();
  }

  protected long connections() {
    if (connections == null) return 0;

    return connections.getCount();
  }

  private boolean shouldMeasure(SocketAddress remoteAddress) {
    return isEnabled() && remoteAddress != null && serverName != null;
  }

  protected static String addressName(SocketAddress address) {
    if (address == null) return null;

    return address.hostAddress() + ":" + address.hostPort();
  }
}
