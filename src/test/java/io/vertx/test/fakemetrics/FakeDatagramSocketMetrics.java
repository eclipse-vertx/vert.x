/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.fakemetrics;

import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeDatagramSocketMetrics extends FakeMetricsBase implements DatagramSocketMetrics {

  private volatile String localName;
  private volatile SocketAddress localAddress;
  private final List<PacketMetric> reads = Collections.synchronizedList(new ArrayList<>());
  private final List<PacketMetric> writes = Collections.synchronizedList(new ArrayList<>());

  public String getLocalName() {
    return localName;
  }

  public SocketAddress getLocalAddress() {
    return localAddress;
  }

  public List<PacketMetric> getReads() {
    return reads;
  }

  public List<PacketMetric> getWrites() {
    return writes;
  }

  @Override
  public void listening(String localName, SocketAddress localAddress) {
    this.localName = localName;
    this.localAddress = localAddress;
  }

  @Override
  public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    reads.add(new PacketMetric(remoteAddress, numberOfBytes));
  }

  @Override
  public void bytesWritten(Void socketMetric, SocketAddress remoteAddress,long numberOfBytes) {
    writes.add(new PacketMetric(remoteAddress, numberOfBytes));
  }

  @Override
  public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {

  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void close() {
  }
}
