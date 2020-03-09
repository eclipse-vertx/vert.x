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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.impl.AddressResolver;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.net.impl.transport.Transport;
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
public class DatagramSocketImpl implements DatagramSocket, MetricsProvider {

  public static DatagramSocketImpl create(VertxInternal vertx, DatagramSocketOptions options) {
    DatagramSocketImpl socket = new DatagramSocketImpl(vertx, options);
    // Make sure object is fully initiliased to avoid race with async registration
    socket.init();
    return socket;
  }

  private final ContextInternal context;
  private final DatagramSocketMetrics metrics;
  private DatagramChannel channel;
  private Handler<io.vertx.core.datagram.DatagramPacket> packetHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private long demand;

  private DatagramSocketImpl(VertxInternal vertx, DatagramSocketOptions options) {
    Transport transport = vertx.transport();
    DatagramChannel channel = transport.datagramChannel(options.isIpV6() ? InternetProtocolFamily.IPv6 : InternetProtocolFamily.IPv4);
    transport.configure(channel, new DatagramSocketOptions(options));
    ContextInternal context = vertx.getOrCreateContext();
    channel.config().setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
    MaxMessagesRecvByteBufAllocator bufAllocator = channel.config().getRecvByteBufAllocator();
    bufAllocator.maxMessagesPerRead(1);
    context.nettyEventLoop().register(channel);
    if (options.getLogActivity()) {
      channel.pipeline().addLast("logging", new LoggingHandler());
    }
    VertxMetrics metrics = vertx.metricsSPI();
    this.metrics = metrics != null ? metrics.createDatagramSocketMetrics(options) : null;
    this.channel = channel;
    this.context = context;
    this.demand = Long.MAX_VALUE;
  }

  private void init() {
    channel.pipeline().addLast("handler", VertxHandler.create(this::createConnection));
  }

  @Override
  public DatagramSocket listenMulticastGroup(String multicastAddress, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = listenMulticastGroup(multicastAddress);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return this;
  }

  @Override
  public Future<Void> listenMulticastGroup(String multicastAddress) {
    ChannelFuture fut;
    try {
      fut = channel.joinGroup(InetAddress.getByName(multicastAddress));
    } catch (UnknownHostException e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public DatagramSocket listenMulticastGroup(String multicastAddress, String networkInterface, String source, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = listenMulticastGroup(multicastAddress, networkInterface, source);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return this;
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
  public DatagramSocket unlistenMulticastGroup(String multicastAddress, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = unlistenMulticastGroup(multicastAddress);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return this;
  }

  @Override
  public Future<Void> unlistenMulticastGroup(String multicastAddress) {
    ChannelFuture fut;
    try {
      fut = channel.leaveGroup(InetAddress.getByName(multicastAddress));
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(promise);
    return promise.future();
  }

  @Override
  public DatagramSocket unlistenMulticastGroup(String multicastAddress, String networkInterface, String source, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = unlistenMulticastGroup(multicastAddress, networkInterface, source);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return this;
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
  public DatagramSocket blockMulticastGroup(String multicastAddress, String networkInterface, String sourceToBlock, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = blockMulticastGroup(multicastAddress, networkInterface, sourceToBlock);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return  this;
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
  public DatagramSocket blockMulticastGroup(String multicastAddress, String sourceToBlock, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = blockMulticastGroup(multicastAddress, sourceToBlock);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return this;
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
  public DatagramSocket listen(int port, String address, Handler<AsyncResult<DatagramSocket>> handler) {
    Objects.requireNonNull(handler, "no null handler accepted");
    listen(SocketAddress.inetSocketAddress(port, address)).onComplete(handler);
    return this;
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
  public DatagramSocketImpl endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public DatagramSocketImpl exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  private Future<DatagramSocket> listen(SocketAddress local) {
    AddressResolver resolver = context.owner().addressResolver();
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

  public synchronized DatagramSocket pause() {
    if (demand > 0L) {
      demand = 0L;
      channel.config().setAutoRead(false);
    }
    return this;
  }

  public synchronized DatagramSocket resume() {
    if (demand == 0L) {
      demand = Long.MAX_VALUE;
      channel.config().setAutoRead(true);
    }
    return this;
  }

  @Override
  public synchronized DatagramSocket fetch(long amount) {
    if (amount < 0L) {
      throw new IllegalArgumentException("Illegal fetch " + amount);
    }
    if (amount > 0L) {
      if (demand == 0L) {
        channel.config().setAutoRead(true);
      }
      demand += amount;
      if (demand < 0L) {
        demand = Long.MAX_VALUE;
      }
    }
    return this;
  }

  @Override
  public DatagramSocket send(Buffer packet, int port, String host, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = send(packet, port, host);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return this;
  }

  @Override
  public Future<Void> send(Buffer packet, int port, String host) {
    Objects.requireNonNull(packet, "no null packet accepted");
    Objects.requireNonNull(host, "no null host accepted");
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port out of range:" + port);
    }
    AddressResolver resolver = context.owner().addressResolver();
    PromiseInternal<Void> promise = context.promise();
    io.netty.util.concurrent.Future<InetSocketAddress> f1 = resolver.resolveHostname(context.nettyEventLoop(), host);
    f1.addListener((GenericFutureListener<io.netty.util.concurrent.Future<InetSocketAddress>>) res1 -> {
      if (res1.isSuccess()) {
        ChannelFuture f2 = channel.writeAndFlush(new DatagramPacket(packet.getByteBuf(), new InetSocketAddress(f1.getNow().getAddress(), port)));
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
  public DatagramSocket send(String str, int port, String host, Handler<AsyncResult<Void>> handler) {
    return send(Buffer.buffer(str), port, host, handler);
  }

  @Override
  public Future<Void> send(String str, int port, String host) {
    return send(Buffer.buffer(str), port, host);
  }

  @Override
  public DatagramSocket send(String str, String enc, int port, String host, Handler<AsyncResult<Void>> handler) {
    return send(Buffer.buffer(str, enc), port, host, handler);
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
  public void close(Handler<AsyncResult<Void>> handler) {
    Future<Void> future = close();
    if (handler != null) {
      future.onComplete(handler);
    }
  }

  @Override
  public synchronized Future<Void> close() {
    // make sure everything is flushed out on close
    if (!channel.isOpen()) {
      return context.succeededFuture();
    }
    channel.flush();
    ChannelFuture future = channel.close();
    PromiseInternal<Void> promise = context.promise();
    future.addListener(promise);
    return promise.future();
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close();
    super.finalize();
  }

  private Connection createConnection(ChannelHandlerContext chctx) {
    return new Connection(context.owner(), chctx, context);
  }

  class Connection extends ConnectionBase {

    public Connection(VertxInternal vertx, ChannelHandlerContext channel, ContextInternal context) {
      super(vertx, channel, context);
    }

    @Override
    public NetworkMetrics metrics() {
      return metrics;
    }

    @Override
    protected void handleInterestedOpsChanged() {
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
      Handler<Void> handler;
      DatagramSocketMetrics metrics;
      synchronized (DatagramSocketImpl.this) {
        handler = endHandler;
        metrics = DatagramSocketImpl.this.metrics;
      }
      if (metrics != null) {
        metrics.close();
      }
      if (handler != null) {
        context.dispatch(null, handler);
      }
    }

    public void handleMessage(Object msg) {
      if (msg instanceof DatagramPacket) {
        DatagramPacket packet = (DatagramPacket) msg;
        ByteBuf content = packet.content();
        if (content.isDirect())  {
          content = VertxHandler.safeBuffer(content, chctx.alloc());
        }
        handlePacket(new DatagramPacketImpl(packet.sender(), Buffer.buffer(content)));
      }
    }

    void handlePacket(io.vertx.core.datagram.DatagramPacket packet) {
      Handler<io.vertx.core.datagram.DatagramPacket> handler;
      synchronized (DatagramSocketImpl.this) {
        if (metrics != null) {
          metrics.bytesRead(null, packet.sender(), packet.data().length());
        }
        if (demand > 0L) {
          if (demand != Long.MAX_VALUE) {
            demand--;
          }
          handler = packetHandler;
        } else {
          handler = null;
        }
      }
      if (handler != null) {
        context.dispatch(packet, handler);
      }
    }
  }
}
