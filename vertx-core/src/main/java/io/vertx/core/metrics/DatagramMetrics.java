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

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class DatagramMetrics extends NetworkMetrics {

  private Counter socketsCounter;
  private Histogram bytesRead;
  private Histogram bytesWritten;

  public DatagramMetrics(VertxInternal vertx) {
    super(vertx, "io.vertx.datagram");
  }

  @Override
  protected void initializeMetrics() {
    // Sort of ugly doing it this way, but we don't want 'connection' metrics, but since UDP uses
    // the ConnectionBase class it's easier to just do it this way...
    this.bytesRead = histogram("bytes-read");
    this.bytesWritten = histogram("bytes-written");
    socketsCounter = counter("sockets");
  }

  @Override
  public void connectionOpened(ConnectionBase connection) {
    // UDP is 'connectionless'
    if (!isEnabled()) return;

    socketsCounter.inc();
  }

  @Override
  public void connectionClosed(ConnectionBase connection) {
    // UDP is 'connectionless'
    if (!isEnabled()) return;

    socketsCounter.dec();
  }

  public void bytesRead(long length) {
    if (!isEnabled()) return;

    bytesRead.update(length);
  }

  public void bytesWritten(long length) {
    if (!isEnabled()) return;

    bytesWritten.update(length);
  }
}
