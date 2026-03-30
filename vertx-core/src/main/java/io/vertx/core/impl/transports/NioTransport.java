/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.transports;

import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.*;
import io.vertx.core.spi.transport.Transport;

import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class NioTransport implements Transport {
  /**
   * The NIO transport, always there.
   */
  public static final Transport INSTANCE = new NioTransport();

  private static final long RUNNING_YIELD_NS = TimeUnit.MICROSECONDS
    .toNanos(Integer.getInteger("io.vertx.virtualthread.running.yield.us", 1));

  // Not cached for graalvm
  private static ThreadFactory virtualThreadFactory() {
    try {
      Class<?> builderClass = ClassLoader.getSystemClassLoader().loadClass("java.lang.Thread$Builder");
      Method ofVirtualMethod = Thread.class.getDeclaredMethod("ofVirtual");
      Object builder = ofVirtualMethod.invoke(null);
      Method factoryMethod = builderClass.getDeclaredMethod("factory");
      return (ThreadFactory) factoryMethod.invoke(builder);
    } catch (Exception e) {
      return null;
    }
  }

  private final UnixDomainSocketNioTransport unixDomainSocketNioTransport = UnixDomainSocketNioTransport.load();

  @Override
  public boolean supportsDomainSockets() {
    return unixDomainSocketNioTransport != null;
  }

  @Override
  public SocketAddress convert(io.vertx.core.net.SocketAddress address) {
    if (address.isDomainSocket() && unixDomainSocketNioTransport != null) {
      return unixDomainSocketNioTransport.convert(address);
    } else {
      return Transport.super.convert(address);
    }
  }

  @Override
  public io.vertx.core.net.SocketAddress convert(SocketAddress address) {
    if (unixDomainSocketNioTransport != null && unixDomainSocketNioTransport.isUnixDomainSocketAddress(address)) {
      return unixDomainSocketNioTransport.convert(address);
    }
    return Transport.super.convert(address);
  }

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return NioIoHandler.newFactory();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    switch (family) {
      case IPv4:
        return new NioDatagramChannel(InternetProtocolFamily.IPv4);
      case IPv6:
        return new NioDatagramChannel(InternetProtocolFamily.IPv6);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public ChannelFactory<? extends DatagramChannel> datagramChannelFactory() {
    return NioDatagramChannel::new;
  }

  @Override
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    if (domainSocket) {
      if (unixDomainSocketNioTransport == null) {
        throw new IllegalArgumentException("Domain sockets require JDK 16 and above, or the usage of a native transport");
      }
      return NioDomainSocketChannel::new;
    } else {
      return NioSocketChannel::new;
    }
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    if (domainSocket) {
      if (unixDomainSocketNioTransport == null) {
        throw new IllegalArgumentException("Domain sockets require JDK 16 and above, or the usage of a native transport");
      }
      return NioServerDomainSocketChannel::new;
    }
    return NioServerSocketChannel::new;
  }

  @Override
  public EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio, boolean virtualThreadEventLoops) {
    if (!virtualThreadEventLoops) {
      return Transport.super.eventLoopGroup(type, nThreads, threadFactory, ioRatio);
    }
    ThreadFactory vtFactory = virtualThreadFactory();
    if (vtFactory == null) {
      throw new IllegalStateException("Virtual threads are not available");
    }
    return new MultiThreadIoEventLoopGroup(nThreads, (Executor) null, ioHandlerFactory()) {
      @Override
      protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(this, null, ioHandlerFactory);
        // Create a platform thread via the Vert.x thread factory to obtain the correct name
        // and register it with the blocked thread checker
        Thread platformThread = threadFactory.newThread(() -> {});
        Thread vt = vtFactory.newThread(() -> {
          while (!eventLoop.isShuttingDown()) {
            eventLoop.run(0, RUNNING_YIELD_NS);
            Thread.yield();
            eventLoop.runNonBlockingTasks(RUNNING_YIELD_NS);
            Thread.yield();
          }
          while (!eventLoop.isTerminated()) {
            eventLoop.runNow();
            Thread.yield();
          }
        });
        vt.setName(platformThread.getName());
        eventLoop.setOwningThread(vt);
        vt.start();
        return eventLoop;
      }
    };
  }
}
