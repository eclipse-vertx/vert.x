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
import io.vertx.core.metrics.spi.DatagramMetrics;
import io.vertx.core.net.SocketAddress;

import static io.vertx.core.metrics.impl.NetMetricsImpl.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class DatagramMetricsImpl extends AbstractMetrics implements DatagramMetrics {

  private Counter socketsCounter;
  private Histogram bytesRead;
  private Histogram bytesWritten;
  private Counter exceptions;
  private String serverName;

  public DatagramMetricsImpl(AbstractMetrics metrics, String baseName) {
    super(metrics.registry(), baseName);
    if (isEnabled()) {
      socketsCounter = counter("sockets");
      exceptions = counter("exceptions");
      bytesWritten = histogram("bytes-written");
    }
  }

  @Override
  public void newSocket() {
    if (!isEnabled()) return;

    socketsCounter.inc();
  }

  @Override
  public void closed() {
    if (!isEnabled()) return;

    socketsCounter.dec();
    if (serverName != null) {
      remove(serverName, "bytes-read");
    }
  }

  @Override
  public void listening(SocketAddress localAddress) {
    if (!isEnabled()) return;

    serverName = addressName(localAddress);
    bytesRead = histogram(serverName, "bytes-read");
  }

  @Override
  public void bytesRead(SocketAddress remoteAddress, long numberOfBytes) {
    if (!isEnabled()) return;

    if (bytesRead != null) {
      bytesRead.update(numberOfBytes);
    }
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
}
