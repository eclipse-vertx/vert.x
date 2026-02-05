/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.vertx.core.spi.metrics.TransportMetrics;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeTransportMetrics extends FakeMetricsBase implements TransportMetrics<ConnectionMetric> {

  private final String name;
  private final String protocol;
  private final AtomicInteger count = new AtomicInteger();
  private final ConcurrentMap<SocketAddress, ConnectionMetric[]> sockets = new ConcurrentHashMap<>();

  public FakeTransportMetrics(String name, String protocol) {
    this.name = name;
    this.protocol = protocol;
  }

  public String protocol() {
    return protocol;
  }

  public int connectionCount() {
    return count.get();
  }

  public Integer connectionCount(SocketAddress socket) {
    socket = keyOf(socket);
    return sockets.getOrDefault(socket, new ConnectionMetric[0]).length;
  }

  public String name() {
    return name;
  }

  private SocketAddress keyOf(SocketAddress addr) {
    if ("localhost".equals(addr.hostName())) {
      addr = SocketAddress.inetSocketAddress(addr.port(), "127.0.0.1");
    }
    return addr;
  }

  public ConnectionMetric connected(SocketAddress remoteAddress, String remoteName) {
    remoteAddress = keyOf(remoteAddress);
    ConnectionMetric metric = new ConnectionMetric(remoteAddress, remoteName);
    sockets.compute(remoteAddress, (key, value) -> {
      if (value == null) {
        value = new ConnectionMetric[] { metric };
      } else {
        value = Arrays.copyOf(value, value.length + 1);
        value[value.length - 1] = metric;
      }
      return value;
    });
    count.incrementAndGet();
    return metric;
  }

  public void disconnected(ConnectionMetric connectionMetric, SocketAddress remoteAddress) {
    remoteAddress = keyOf(remoteAddress);
    sockets.compute(remoteAddress, (key, value) -> {
      if (value != null) {
        for (int idx = 0;idx < value.length;idx++) {
          if (value[idx] == connectionMetric) {
            ConnectionMetric[] next = new ConnectionMetric[value.length - 1];
            System.arraycopy(value, 0, next, 0, idx);
            System.arraycopy(value, idx + 1, next, idx, next.length - idx);
            if (next.length == 0) {
              next = null;
            }
            return next;
          }
        }
      }
      return null;
    });
    if (connectionMetric.connected.compareAndSet(true, false)) {
      count.decrementAndGet();
    }
  }

  @Override
  public void streamOpened(ConnectionMetric connectionMetric) {
    connectionMetric.openStreams.incrementAndGet();
  }

  @Override
  public void streamClosed(ConnectionMetric connectionMetric) {
    connectionMetric.openStreams.decrementAndGet();
  }

  public ConnectionMetric firstMetric(SocketAddress address) {
    address = keyOf(address);
    return sockets.get(address)[0];
  }

  @Override
  public void bytesRead(ConnectionMetric connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
    connectionMetric.bytesRead.addAndGet(numberOfBytes);
    connectionMetric.bytesReadEvents.add(numberOfBytes);
  }

  @Override
  public void bytesWritten(ConnectionMetric connectionMetric, SocketAddress remoteAddress, long numberOfBytes) {
    connectionMetric.bytesWritten.addAndGet(numberOfBytes);
    connectionMetric.bytesWrittenEvents.add(numberOfBytes);
  }
}
