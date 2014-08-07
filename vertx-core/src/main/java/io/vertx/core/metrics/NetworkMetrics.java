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

package io.vertx.core.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;

import static com.codahale.metrics.MetricRegistry.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public abstract class NetworkMetrics extends AbstractMetrics {
  private Counter connections;
  private Histogram bytesRead;
  private Histogram bytesWritten;

  public NetworkMetrics(VertxInternal vertx, String baseName) {
    super(vertx, baseName);
    if (isEnabled()) {
      this.connections = counter("connections");

      this.bytesRead = histogram("bytes-read");
      this.bytesWritten = histogram("bytes-written");
    }
  }

  public void connectionOpened(ConnectionBase connection) {
    if (!isEnabled()) return;

    connections.inc();
    remoteAddressCounter(connection).inc();
  }

  public void connectionClosed(ConnectionBase connection) {
    if (!isEnabled()) return;

    connections.dec();
    remoteAddressCounter(connection).dec();
  }

  public void bytesRead(long length) {
    if (!isEnabled()) return;

    bytesRead.update(length);
  }

  public void bytesWritten(long length) {
    if (!isEnabled()) return;

    bytesWritten.update(length);
  }

  protected long connections() {
    if (connections == null) return 0;

    return connections.getCount();
  }

  private Counter remoteAddressCounter(ConnectionBase connection) {
    return counter("connections", connection.remoteAddress().hostAddress());
  }

  protected static String addressName(String baseName, String host, int port) {
    return name(baseName, host + ":" + port);
  }
}
