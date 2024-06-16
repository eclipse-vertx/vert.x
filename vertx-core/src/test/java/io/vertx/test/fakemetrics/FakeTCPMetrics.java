package io.vertx.test.fakemetrics;

import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeTCPMetrics extends FakeMetricsBase implements TCPMetrics<SocketMetric> {


  private final AtomicInteger count = new AtomicInteger();
  private final ConcurrentMap<SocketAddress, SocketMetric[]> sockets = new ConcurrentHashMap<>();

  public int connectionCount() {
    return count.get();
  }

  public Integer connectionCount(SocketAddress socket) {
    return sockets.getOrDefault(socket, new SocketMetric[0]).length;
  }

  public SocketMetric connected(SocketAddress remoteAddress, String remoteName) {
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
