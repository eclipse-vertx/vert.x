/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.datagram.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.impl.HostnameResolver;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.VertxConnection;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.metrics.*;
import io.vertx.core.streams.WriteStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DatagramSocketImpl implements DatagramSocket, MetricsProvider, Closeable {

  public static DatagramSocketImpl create(VertxInternal vertx, CloseFuture closeFuture, DatagramSocketOptions options) {
    DatagramSocketImpl socket = new DatagramSocketImpl(vertx, closeFuture, options);
    // Make sure object is fully initiliased to avoid race with async registration
    socket.init();
    return socket;
  }

  private final ContextInternal context;
  private final DatagramSocketMetrics metrics;
  private DatagramChannel channel;
  private Handler<io.vertx.core.datagram.DatagramPacket> packetHandler;
  private Handler<Throwable> exceptionHandler;
  private final CloseFuture closeFuture;

  private DatagramSocketImpl(VertxInternal vertx, CloseFuture closeFuture, DatagramSocketOptions options) {
    Transport transport = vertx.transport();
    DatagramChannel channel = transport.datagramChannel(options.isIpV6() ? InternetProtocolFamily.IPv6 : InternetProtocolFamily.IPv4);
    transport.configure(channel, new DatagramSocketOptions(options));
    ContextInternal context = vertx.getOrCreateContext();
    channel.config().setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
    MaxMessagesRecvByteBufAllocator bufAllocator = channel.config().getRecvByteBufAllocator();
    bufAllocator.maxMessagesPerRead(1);
    context.nettyEventLoop().register(channel);
    if (options.getLogActivity()) {
      channel.pipeline().addLast("logging", new LoggingHandler(options.getActivityLogDataFormat()));
    }
    VertxMetrics metrics = vertx.metricsSPI();
    this.metrics = metrics != null ? metrics.createDatagramSocketMetrics(options) : null;
    this.channel = channel;
    this.context = context;
    this.closeFuture = closeFuture;
  }

  private void init() {
    channel.pipeline().addLast("handler", VertxHandler.create(this::createConnection));
  }

  private NetworkInterface determineMulticastNetworkIface() throws Exception {
    NetworkInterface iface = null;
    InetSocketAddress localAddr = channel.localAddress();
    if (localAddr != null) {
      iface = NetworkInterface.getByInetAddress(localAddr.getAddress());
    }
    if (iface == null) {
      iface = channel.config().getNetworkInterface();
    }
    return iface;
  }

  @Override
  public Future<Void> listenMulticastGroup(String multicastAddress) {
    NetworkInterface iface;
    try {
      iface = determineMulticastNetworkIface();
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    if (iface == null) {
      return context.failedFuture("A valid network interface could not be determined from the socket bind address or multicast interface");
    }
    ChannelFuture fut;
    try {
      fut = channel.joinGroup(InetAddress.getByName(multicastAddress), iface, null);
    } catch (UnknownHostException e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public Future<Void> listenMulticastGroup(String multicastAddress, String networkInterface, @Nullable String source) {
    ChannelFuture fut;
    try {
      InetAddress sourceAddress;
      if (source == null) {
        sourceAddress = null;
      } else {
        sourceAddress = InetAddress.getByName(source);
      }
      fut = channel.joinGroup(InetAddress.getByName(multicastAddress), NetworkInterface.getByName(networkInterface), sourceAddress);
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public Future<Void> unlistenMulticastGroup(String multicastAddress) {
    NetworkInterface iface;
    try {
      iface = determineMulticastNetworkIface();
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    if (iface == null) {
      return context.failedFuture("A valid network interface could not be determined from the socket bind address or multicast interface");
    }
    ChannelFuture fut;
    try {
      fut = channel.leaveGroup(InetAddress.getByName(multicastAddress), iface, null);
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public Future<Void> unlistenMulticastGroup(String multicastAddress, String networkInterface, @Nullable String source) {
    ChannelFuture fut;
    try {
      InetAddress sourceAddress;
      if (source == null) {
        sourceAddress = null;
      } else {
        sourceAddress = InetAddress.getByName(source);
      }
      fut = channel.leaveGroup(InetAddress.getByName(multicastAddress), NetworkInterface.getByName(networkInterface), sourceAddress);
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public Future<Void> blockMulticastGroup(String multicastAddress, String networkInterface, String sourceToBlock) {
    ChannelFuture fut;
    try {
      InetAddress sourceAddress;
      if (sourceToBlock == null) {
        sourceAddress = null;
      } else {
        sourceAddress = InetAddress.getByName(sourceToBlock);
      }
      fut = channel.block(InetAddress.getByName(multicastAddress), NetworkInterface.getByName(networkInterface), sourceAddress);
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public Future<Void> blockMulticastGroup(String multicastAddress, String sourceToBlock) {
    ChannelFuture fut;
    try {
      fut = channel.block(InetAddress.getByName(multicastAddress), InetAddress.getByName(sourceToBlock));
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public Future<DatagramSocket> listen(int port, String address) {
    return listen(SocketAddress.inetSocketAddress(port, address));
  }

  @Override
  public synchronized DatagramSocket handler(Handler<io.vertx.core.datagram.DatagramPacket> handler) {
    this.packetHandler = handler;
    return this;
  }
  @Override
  public DatagramSocketImpl exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  private Future<DatagramSocket> listen(SocketAddress local) {
    HostnameResolver resolver = context.owner().hostnameResolver();
    PromiseInternal<Void> promise = context.promise();
    io.netty.util.concurrent.Future<InetSocketAddress> f1 = resolver.resolveHostname(context.nettyEventLoop(), local.host());
    f1.addListener((GenericFutureListener<io.netty.util.concurrent.Future<InetSocketAddress>>) res1 -> {
      if (res1.isSuccess()) {
        ChannelFuture f2 = channel.bind(new InetSocketAddress(res1.getNow().getAddress(), local.port()));
        if (metrics != null) {
          f2.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Void>>) res2 -> {
            if (res2.isSuccess()) {
              metrics.listening(local.host(), localAddress());
            }
          });
        }
        f2.addListener(promise);
      } else {
        promise.fail(res1.cause());
      }
    });
    return promise.future().map(this);
  }

  @Override
  public Future<Void> send(Buffer packet, int port, String host) {
    Objects.requireNonNull(packet, "no null packet accepted");
    Objects.requireNonNull(host, "no null host accepted");
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port out of range:" + port);
    }
    HostnameResolver resolver = context.owner().hostnameResolver();
    PromiseInternal<Void> promise = context.promise();
    io.netty.util.concurrent.Future<InetSocketAddress> f1 = resolver.resolveHostname(context.nettyEventLoop(), host);
    f1.addListener((GenericFutureListener<io.netty.util.concurrent.Future<InetSocketAddress>>) res1 -> {
      if (res1.isSuccess()) {
        ChannelFuture f2 = channel.writeAndFlush(new DatagramPacket(((BufferInternal)packet).getByteBuf(), new InetSocketAddress(f1.getNow().getAddress(), port)));
        if (metrics != null) {
          f2.addListener(fut -> {
            if (fut.isSuccess()) {
              metrics.bytesWritten(null, SocketAddress.inetSocketAddress(port, host), packet.length());
            }
          });
        }
        f2.addListener(promise);
      } else {
        promise.fail(res1.cause());
      }
    });
    return promise.future();
  }

  @Override
  public WriteStream<Buffer> sender(int port, String host) {
    Arguments.requireInRange(port, 0, 65535, "port p must be in range 0 <= p <= 65535");
    Objects.requireNonNull(host, "no null host accepted");
    return new PacketWriteStreamImpl(this, port, host);
  }

  @Override
  public Future<Void> send(String str, int port, String host) {
    return send(Buffer.buffer(str), port, host);
  }

  @Override
  public Future<Void> send(String str, String enc, int port, String host) {
    return send(Buffer.buffer(str, enc), port, host);
  }

  @Override
  public SocketAddress localAddress() {
    return context.owner().transport().convert(channel.localAddress());
  }

  @Override
  public synchronized Future<Void> close() {
    ContextInternal closingCtx = context.owner().getOrCreateContext();
    PromiseInternal<Void> promise = closingCtx.promise();
    closeFuture.close(promise);
    return promise.future();
  }

  @Override
  public void close(Promise<Void> completion) {
    if (!channel.isOpen()) {
      completion.complete();
    } else {
      // make sure everything is flushed out on close
      channel.flush();
      ChannelFuture future = channel.close();
      future.addListener((PromiseInternal<Void>)completion);
    }
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  private Connection createConnection(ChannelHandlerContext chctx) {
    return new Connection(context, chctx);
  }

  class Connection extends VertxConnection {

    public Connection(ContextInternal context, ChannelHandlerContext channel) {
      super(context, channel);
    }

    @Override
    public NetworkMetrics metrics() {
      return metrics;
    }

    @Override
    protected void handleException(Throwable t) {
      super.handleException(t);
      Handler<Throwable> handler;
      synchronized (DatagramSocketImpl.this) {
        handler = exceptionHandler;
      }
      if (handler != null) {
        handler.handle(t);
      }
    }

    @Override
    protected void handleClosed() {
      super.handleClosed();
      DatagramSocketMetrics metrics;
      synchronized (DatagramSocketImpl.this) {
        metrics = DatagramSocketImpl.this.metrics;
      }
      if (metrics != null) {
        metrics.close();
      }
    }

    public void handleMessage(Object msg) {
      if (msg instanceof DatagramPacket) {
        DatagramPacket packet = (DatagramPacket) msg;
        ByteBuf content = packet.content();
        Buffer buffer = BufferInternal.safeBuffer(content);
        handlePacket(new DatagramPacketImpl(packet.sender(), buffer));
      }
    }

    void handlePacket(io.vertx.core.datagram.DatagramPacket packet) {
      Handler<io.vertx.core.datagram.DatagramPacket> handler;
      synchronized (DatagramSocketImpl.this) {
        if (metrics != null) {
          metrics.bytesRead(null, packet.sender(), packet.data().length());
        }
        handler = packetHandler;
      }
      if (handler != null) {
        context.emit(packet, handler);
      }
    }
  }
}
