/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
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
import io.vertx.core.spi.transport.Transport;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A transport that uses NIO channels but runs each Netty event loop as a long-running virtual thread
 * using {@link ManualIoEventLoop}. This allows the JVM's virtual thread scheduler (ForkJoinPool) to
 * multiplex event loops onto platform threads alongside other virtual threads.
 */
public class VirtualThreadNioTransport implements Transport {

  private static final long RUNNING_YIELD_NS = TimeUnit.MICROSECONDS
    .toNanos(Integer.getInteger("io.vertx.virtualthread.running.yield.us", 1));

  private static final ThreadFactory VIRTUAL_THREAD_FACTORY;
  private static final Throwable UNAVAILABILITY_CAUSE;

  static {
    ThreadFactory factory = null;
    Throwable cause = null;
    try {
      Class<?> builderClass = ClassLoader.getSystemClassLoader().loadClass("java.lang.Thread$Builder");
      Method ofVirtualMethod = Thread.class.getDeclaredMethod("ofVirtual");
      Object builder = ofVirtualMethod.invoke(null);
      Method factoryMethod = builderClass.getDeclaredMethod("factory");
      factory = (ThreadFactory) factoryMethod.invoke(builder);
    } catch (Exception e) {
      cause = e;
    }
    VIRTUAL_THREAD_FACTORY = factory;
    UNAVAILABILITY_CAUSE = cause;
  }

  public static final Transport INSTANCE = new VirtualThreadNioTransport();

  private final NioTransport delegate = (NioTransport) NioTransport.INSTANCE;

  @Override
  public boolean isAvailable() {
    return VIRTUAL_THREAD_FACTORY != null;
  }

  @Override
  public Throwable unavailabilityCause() {
    return UNAVAILABILITY_CAUSE;
  }

  @Override
  public boolean supportsDomainSockets() {
    return delegate.supportsDomainSockets();
  }

  @Override
  public java.net.SocketAddress convert(io.vertx.core.net.SocketAddress address) {
    return delegate.convert(address);
  }

  @Override
  public io.vertx.core.net.SocketAddress convert(java.net.SocketAddress address) {
    return delegate.convert(address);
  }

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return NioIoHandler.newFactory();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    return delegate.datagramChannel(family);
  }

  @Override
  public ChannelFactory<? extends DatagramChannel> datagramChannelFactory() {
    return delegate.datagramChannelFactory();
  }

  @Override
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    return delegate.channelFactory(domainSocket);
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    return delegate.serverChannelFactory(domainSocket);
  }

  @Override
  public EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio) {
    if (VIRTUAL_THREAD_FACTORY == null) {
      throw new IllegalStateException("Virtual threads are not available", UNAVAILABILITY_CAUSE);
    }
    return new MultiThreadIoEventLoopGroup(nThreads, (Executor) null, ioHandlerFactory()) {
      @Override
      protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
        ManualIoEventLoop eventLoop = new ManualIoEventLoop(this, null, ioHandlerFactory);
        // Create a platform thread via the Vert.x thread factory to obtain the correct name
        // and register it with the blocked thread checker. Then name the virtual thread the same.
        Thread platformThread = threadFactory.newThread(() -> {});
        Thread vt = VIRTUAL_THREAD_FACTORY.newThread(() -> {
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
