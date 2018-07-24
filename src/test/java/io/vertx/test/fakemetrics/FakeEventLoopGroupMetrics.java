package io.vertx.test.fakemetrics;

import io.netty.channel.MultithreadEventLoopGroup;
import io.vertx.core.net.impl.transport.Transport;

/**
 * @author marcus
 * @since 1.0.0
 */
public class FakeEventLoopGroupMetrics {

  private final Class<? extends Transport> transport;
  private final String name;
  private final MultithreadEventLoopGroup eventLoopGroup;

  public FakeEventLoopGroupMetrics(final Class<? extends Transport> transport, final String name, final MultithreadEventLoopGroup eventLoopGroup) {
    this.transport = transport;
    this.name = name;
    this.eventLoopGroup = eventLoopGroup;
  }

  public Class<? extends Transport> getTransport() {
    return transport;
  }

  public String getName() {
    return name;
  }

  public MultithreadEventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }
}
