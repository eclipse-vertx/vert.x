package io.vertx.test.fakemetrics;

import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.vertx.core.net.impl.transport.Transport;

/**
 * @author marcus
 * @since 1.0.0
 */
public class FakeEventLoopMetrics {

  private final Class<? extends Transport> transport;
  private final String name;
  private final MultithreadEventLoopGroup eventLoopGroup;
  private final SingleThreadEventLoop eventLoop;

  public FakeEventLoopMetrics(final Class<? extends Transport> transport, final String name, final MultithreadEventLoopGroup eventLoopGroup, final SingleThreadEventLoop eventLoop) {
    this.transport = transport;
    this.name = name;
    this.eventLoopGroup = eventLoopGroup;
    this.eventLoop = eventLoop;
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

  public SingleThreadEventLoop getEventLoop() {
    return eventLoop;
  }
}
