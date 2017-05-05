/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.datagram.impl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LoggingHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.datagram.PacketWritestream;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetworkOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.NetworkMetrics;

import java.net.*;
import java.util.Objects;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
// TODO: 16/12/18 by zmyer
public class DatagramSocketImpl extends ConnectionBase implements DatagramSocket, MetricsProvider {
    //报文处理对象
    private Handler<io.vertx.core.datagram.DatagramPacket> packetHandler;

    // TODO: 16/12/18 by zmyer
    public DatagramSocketImpl(VertxInternal vertx, DatagramSocketOptions options) {
        super(vertx, createChannel(options.isIpV6() ? io.vertx.core.datagram.impl.InternetProtocolFamily.IPv6 : io.vertx.core.datagram.impl.InternetProtocolFamily.IPv4,
                new DatagramSocketOptions(options)), vertx.getOrCreateContext(), options);
        //读取执行上下文对象
        ContextImpl creatingContext = vertx.getContext();
        if (creatingContext != null && creatingContext.isMultiThreadedWorkerContext()) {
            throw new IllegalStateException("Cannot use DatagramSocket in a multi-threaded worker verticle");
        }
        //设置通道对象属性
        channel().config().setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
        //开始将该通道对象注册到netty事件循环对象中
        context.nettyEventLoop().register(channel);
        if (options.getLogActivity()) {
            channel().pipeline().addLast("logging", new LoggingHandler());
        }
        //注册数据报文处理句柄
        channel.pipeline().addLast("handler", new DatagramServerHandler(this));
        channel().config().setMaxMessagesPerRead(1);
        channel().config().setAllocator(PartialPooledByteBufAllocator.INSTANCE);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    protected NetworkMetrics createMetrics(NetworkOptions options) {
        return vertx.metricsSPI().createMetrics(this, (DatagramSocketOptions) options);
    }

    @Override
    protected Object metric() {
        return null;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket listenMulticastGroup(String multicastAddress, Handler<AsyncResult<DatagramSocket>> handler) {
        try {
            //设置监听器
            addListener(channel().joinGroup(InetAddress.getByName(multicastAddress)), handler);
        } catch (UnknownHostException e) {
            notifyException(handler, e);
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket listenMulticastGroup(String multicastAddress, String networkInterface, String source, Handler<AsyncResult<DatagramSocket>> handler) {
        try {
            InetAddress sourceAddress;
            if (source == null) {
                sourceAddress = null;
            } else {
                sourceAddress = InetAddress.getByName(source);
            }
            //设置监听器
            addListener(channel().joinGroup(InetAddress.getByName(multicastAddress),
                    NetworkInterface.getByName(networkInterface), sourceAddress), handler);
        } catch (Exception e) {
            notifyException(handler, e);
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket unlistenMulticastGroup(String multicastAddress, Handler<AsyncResult<DatagramSocket>> handler) {
        try {
            //设置监听器
            addListener(channel().leaveGroup(InetAddress.getByName(multicastAddress)), handler);
        } catch (UnknownHostException e) {
            notifyException(handler, e);
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket unlistenMulticastGroup(String multicastAddress, String networkInterface, String source, Handler<AsyncResult<DatagramSocket>> handler) {
        try {
            InetAddress sourceAddress;
            if (source == null) {
                sourceAddress = null;
            } else {
                sourceAddress = InetAddress.getByName(source);
            }
            //设置监听器
            addListener(channel().leaveGroup(InetAddress.getByName(multicastAddress),
                    NetworkInterface.getByName(networkInterface), sourceAddress), handler);
        } catch (Exception e) {
            notifyException(handler, e);
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket blockMulticastGroup(String multicastAddress, String networkInterface, String sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler) {
        try {
            InetAddress sourceAddress;
            if (sourceToBlock == null) {
                sourceAddress = null;
            } else {
                sourceAddress = InetAddress.getByName(sourceToBlock);
            }
            //设置监听器
            addListener(channel().block(InetAddress.getByName(multicastAddress),
                    NetworkInterface.getByName(networkInterface), sourceAddress), handler);
        } catch (Exception e) {
            notifyException(handler, e);
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket blockMulticastGroup(String multicastAddress, String sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler) {
        try {
            addListener(channel().block(InetAddress.getByName(multicastAddress), InetAddress.getByName(sourceToBlock)), handler);
        } catch (UnknownHostException e) {
            notifyException(handler, e);
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket listen(int port, String address, Handler<AsyncResult<DatagramSocket>> handler) {
        return listen(new SocketAddressImpl(port, address), handler);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public synchronized DatagramSocket handler(Handler<io.vertx.core.datagram.DatagramPacket> handler) {
        this.packetHandler = handler;
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocketImpl endHandler(Handler<Void> endHandler) {
        return (DatagramSocketImpl) super.closeHandler(endHandler);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocketImpl exceptionHandler(Handler<Throwable> handler) {
        return (DatagramSocketImpl) super.exceptionHandler(handler);
    }

    // TODO: 16/12/18 by zmyer
    private DatagramSocket listen(SocketAddress local, Handler<AsyncResult<DatagramSocket>> handler) {
        Objects.requireNonNull(handler, "no null handler accepted");
        vertx.resolveAddress(local.host(), res -> {
            if (res.succeeded()) {
                //开始进入监听流程
                ChannelFuture future = channel().bind(new InetSocketAddress(res.result(), local.port()));
                //设置监听器
                addListener(future, ar -> {
                    if (ar.succeeded()) {
                        ((DatagramSocketMetrics) metrics).listening(local.host(), localAddress());
                    }
                    //开始使用事件处理对象处理结果
                    handler.handle(ar);
                });
            } else {
                //处理失败结果
                handler.handle(Future.failedFuture(res.cause()));
            }
        });

        return this;
    }

    // TODO: 16/12/18 by zmyer
    @SuppressWarnings("unchecked")
    final void addListener(ChannelFuture future, Handler<AsyncResult<DatagramSocket>> handler) {
        if (handler != null) {
            //为每个通道异步对象设置监听器
            future.addListener(new DatagramChannelFutureListener<>(this, handler, context));
        }
    }

    // TODO: 16/12/18 by zmyer
    @SuppressWarnings("unchecked")
    public DatagramSocket pause() {
        doPause();
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @SuppressWarnings("unchecked")
    public DatagramSocket resume() {
        doResume();
        return this;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    @SuppressWarnings("unchecked")
    public DatagramSocket send(Buffer packet, int port, String host, Handler<AsyncResult<DatagramSocket>> handler) {
        Objects.requireNonNull(packet, "no null packet accepted");
        Objects.requireNonNull(host, "no null host accepted");
        InetSocketAddress addr = InetSocketAddress.createUnresolved(host, port);
        //地址解析失败
        if (addr.isUnresolved()) {
            vertx.resolveAddress(host, res -> {
                if (res.succeeded()) {
                    //开始发送指定的报文
                    doSend(packet, new InetSocketAddress(res.result(), port), handler);
                } else {
                    //处理发送失败结果
                    handler.handle(Future.failedFuture(res.cause()));
                }
            });
        } else {
            // If it's immediately resolved it means it was just an IP address so no need to async resolve
            //立即发送报文
            doSend(packet, addr, handler);
        }
        if (metrics.isEnabled()) {
            //发送统计日志
            metrics.bytesWritten(null, new SocketAddressImpl(port, host), packet.length());
        }
        return this;
    }

    // TODO: 16/12/18 by zmyer
    private void doSend(Buffer packet, InetSocketAddress addr, Handler<AsyncResult<DatagramSocket>> handler) {
        //开始发送数据报文
        ChannelFuture future = channel().writeAndFlush(new DatagramPacket(packet.getByteBuf(), addr));
        //为通道异步对象设置监听器对象
        addListener(future, handler);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public PacketWritestream sender(int port, String host) {
        Arguments.requireInRange(port, 0, 65535, "port p must be in range 0 <= p <= 65535");
        Objects.requireNonNull(host, "no null host accepted");
        //创建数据报文流对象
        return new PacketWriteStreamImpl(this, port, host);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket send(String str, int port, String host, Handler<AsyncResult<DatagramSocket>> handler) {
        //发送报文
        return send(Buffer.buffer(str), port, host, handler);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public DatagramSocket send(String str, String enc, int port, String host, Handler<AsyncResult<DatagramSocket>> handler) {
        //发送报文
        return send(Buffer.buffer(str, enc), port, host, handler);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void close(final Handler<AsyncResult<Void>> handler) {
        // make sure everything is flushed out on close
        endReadAndFlush();
        metrics.close();
        ChannelFuture future = channel.close();
        if (handler != null) {
            future.addListener(new DatagramChannelFutureListener<>(null, handler, context));
        }
    }

    @Override
    public boolean isMetricsEnabled() {
        return metrics != null && metrics.isEnabled();
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    protected DatagramChannel channel() {
        return (DatagramChannel) channel;
    }

    // TODO: 16/12/18 by zmyer
    private static NioDatagramChannel createChannel(io.vertx.core.datagram.impl.InternetProtocolFamily family,
                                                    DatagramSocketOptions options) {
        NioDatagramChannel channel;
        if (family == null) {
            //创建非阻塞数据报文通道对象
            channel = new NioDatagramChannel();
        } else {
            switch (family) {
                case IPv4:
                    channel = new NioDatagramChannel(InternetProtocolFamily.IPv4);
                    break;
                case IPv6:
                    channel = new NioDatagramChannel(InternetProtocolFamily.IPv6);
                    break;
                default:
                    channel = new NioDatagramChannel();
            }
        }
        if (options.getSendBufferSize() != -1) {
            //设置发送缓冲区大小
            channel.config().setSendBufferSize(options.getSendBufferSize());
        }
        if (options.getReceiveBufferSize() != -1) {
            //设置接受缓冲区大小
            channel.config().setReceiveBufferSize(options.getReceiveBufferSize());
            //设置接收缓冲区分配器对象
            channel.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
        }
        //设置重连标记
        channel.config().setReuseAddress(options.isReuseAddress());
        if (options.getTrafficClass() != -1) {
            //设置拥塞控制类对象
            channel.config().setTrafficClass(options.getTrafficClass());
        }
        channel.config().setBroadcast(options.isBroadcast());
        channel.config().setLoopbackModeDisabled(options.isLoopbackModeDisabled());
        if (options.getMulticastTimeToLive() != -1) {
            channel.config().setTimeToLive(options.getMulticastTimeToLive());
        }
        if (options.getMulticastNetworkInterface() != null) {
            try {
                channel.config().setNetworkInterface(NetworkInterface.getByName(options.getMulticastNetworkInterface()));
            } catch (SocketException e) {
                throw new IllegalArgumentException("Could not find network interface with name " + options.getMulticastNetworkInterface());
            }
        }
        return channel;
    }

    // TODO: 16/12/18 by zmyer
    private void notifyException(final Handler<AsyncResult<DatagramSocket>> handler, final Throwable cause) {
        //处理异常
        context.executeFromIO(() -> handler.handle(Future.failedFuture(cause)));
    }

    // TODO: 16/12/18 by zmyer
    @Override
    protected void finalize() throws Throwable {
        // Make sure this gets cleaned up if there are no more references to it
        // so as not to leave connections and resources dangling until the system is shutdown
        // which could make the JVM run out of file handles.
        close();
        super.finalize();
    }

    // TODO: 16/12/18 by zmyer
    protected void handleClosed() {
        checkContext();
        super.handleClosed();
    }

    // TODO: 16/12/18 by zmyer
    synchronized void handlePacket(io.vertx.core.datagram.DatagramPacket packet) {
        if (metrics.isEnabled()) {
            metrics.bytesRead(null, packet.sender(), packet.data().length());
        }
        if (packetHandler != null) {
            //开始处理数据报文
            packetHandler.handle(packet);
        }
    }

    @Override
    protected void handleInterestedOpsChanged() {
    }
}
