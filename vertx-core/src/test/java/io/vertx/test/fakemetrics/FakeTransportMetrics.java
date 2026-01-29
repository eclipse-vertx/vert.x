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

public class FakeTransportMetrics extends FakeMetricsBase implements TransportMetrics<SocketMetric> {

  private final String name;
  private final AtomicInteger count = new AtomicInteger();
  private final ConcurrentMap<SocketAddress, SocketMetric[]> sockets = new ConcurrentHashMap<>();

  public FakeTransportMetrics(String name) {
    this.name = name;
  }

  public int connectionCount() {
    return count.get();
  }

  public Integer connectionCount(SocketAddress socket) {
    socket = keyOf(socket);
    return sockets.getOrDefault(socket, new SocketMetric[0]).length;
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

  public SocketMetric connected(SocketAddress remoteAddress, String remoteName) {
    remoteAddress = keyOf(remoteAddress);
    SocketMetric metric = new SocketMetric(remoteAddress, remoteName);
    sockets.compute(remoteAddress, (key, value) -> {
      if (value == null) {
        value = new SocketMetric[] { metric };
      } else {
        value = Arrays.copyOf(value, value.length + 1);
        value[value.length - 1] = metric;
      }
      return value;
    });
    count.incrementAndGet();
    return metric;
  }

  public void disconnected(SocketMetric socketMetric, SocketAddress remoteAddress) {
    remoteAddress = keyOf(remoteAddress);
    sockets.compute(remoteAddress, (key, value) -> {
      if (value != null) {
        for (int idx = 0;idx < value.length;idx++) {
          if (value[idx] == socketMetric) {
            SocketMetric[] next = new SocketMetric[value.length - 1];
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
    if (socketMetric.connected.compareAndSet(true, false)) {
      count.decrementAndGet();
    }
  }

  public SocketMetric firstMetric(SocketAddress address) {
    address = keyOf(address);
    return sockets.get(address)[0];
  }

  @Override
  public void bytesRead(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    socketMetric.bytesRead.addAndGet(numberOfBytes);
    socketMetric.bytesReadEvents.add(numberOfBytes);
  }

  @Override
  public void bytesWritten(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    socketMetric.bytesWritten.addAndGet(numberOfBytes);
    socketMetric.bytesWrittenEvents.add(numberOfBytes);
  }
}
