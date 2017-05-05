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

package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.TCPMetrics;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 * <p>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 17/1/1 by zmyer
public class NetSocketImpl extends ConnectionBase implements NetSocket {

    private static final Logger log = LoggerFactory.getLogger(NetSocketImpl.class);
    //写入事件处理器ID
    private final String writeHandlerID;
    //消息消费者
    private final MessageConsumer registration;
    //SSL
    private final SSLHelper helper;
    //主机名
    private final String host;
    //端口号
    private final int port;
    //统计对象
    private Object metric;
    //数据处理器对象
    private Handler<Buffer> dataHandler;
    //结束事件处理器对象
    private Handler<Void> endHandler;
    //
    private Handler<Void> drainHandler;
    //挂起缓冲区
    private Buffer pendingData;
    //是否暂停
    private boolean paused = false;
    //异步写入对象
    private ChannelFuture writeFuture;

    // TODO: 17/1/1 by zmyer
    public NetSocketImpl(VertxInternal vertx, Channel channel, ContextImpl context,
                         SSLHelper helper, TCPMetrics metrics, Object metric) {
        this(vertx, channel, null, 0, context, helper, metrics, metric);
    }

    // TODO: 17/1/1 by zmyer
    public NetSocketImpl(VertxInternal vertx, Channel channel, String host, int port, ContextImpl context,
                         SSLHelper helper, TCPMetrics metrics, Object metric) {
        super(vertx, channel, context, metrics);
        this.helper = helper;
        this.writeHandlerID = UUID.randomUUID().toString();
        this.metric = metric;
        this.host = host;
        this.port = port;
        Handler<Message<Buffer>> writeHandler = msg -> write(msg.body());
        registration = vertx.eventBus().<Buffer>localConsumer(writeHandlerID).handler(writeHandler);
    }

    protected synchronized void setMetric(Object metric) {
        this.metric = metric;
    }

    @Override
    protected synchronized Object metric() {
        return metric;
    }

    @Override
    public String writeHandlerID() {
        return writeHandlerID;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetSocket write(Buffer data) {
        ByteBuf buf = data.getByteBuf();
        write(buf);
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetSocket write(String str) {
        write(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8));
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetSocket write(String str, String enc) {
        if (enc == null) {
            write(str);
        } else {
            write(Unpooled.copiedBuffer(str, Charset.forName(enc)));
        }
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized NetSocket handler(Handler<Buffer> dataHandler) {
        this.dataHandler = dataHandler;
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized NetSocket pause() {
        if (!paused) {
            paused = true;
            doPause();
        }
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized NetSocket resume() {
        if (paused) {
            paused = false;
            if (pendingData != null) {
                // Send empty buffer to trigger sending of pending data
                //开始处理已经接收到的数据
                context.runOnContext(v -> handleDataReceived(Buffer.buffer()));
            }
            //恢复流程
            doResume();
        }
        return this;
    }

    @Override
    public NetSocket setWriteQueueMaxSize(int maxSize) {
        doSetWriteQueueMaxSize(maxSize);
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        return isNotWritable();
    }

    @Override
    public synchronized NetSocket endHandler(Handler<Void> endHandler) {
        this.endHandler = endHandler;
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized NetSocket drainHandler(Handler<Void> drainHandler) {
        this.drainHandler = drainHandler;
        vertx.runOnContext(v -> callDrainHandler()); //If the channel is already drained, we want to call it immediately
        return this;
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetSocket sendFile(String filename, long offset, long length) {
        return sendFile(filename, offset, length, null);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetSocket sendFile(String filename, long offset, long length, final Handler<AsyncResult<Void>> resultHandler) {
        //开始读取文件
        File f = vertx.resolveFile(filename);
        if (f.isDirectory()) {
            throw new IllegalArgumentException("filename must point to a file and not to a directory");
        }
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            //发送文件
            ChannelFuture future = super.sendFile(raf, Math.min(offset, f.length()), Math.min(length, f.length() - offset));
            if (resultHandler != null) {
                future.addListener(fut -> {
                    final AsyncResult<Void> res;
                    if (future.isSuccess()) {
                        //发送成功
                        res = Future.succeededFuture();
                    } else {
                        //发送失败
                        res = Future.failedFuture(future.cause());
                    }
                    //开始在执行上下文对象中处理结果
                    vertx.runOnContext(v -> resultHandler.handle(res));
                });
            }
        } catch (IOException e) {
            try {
                if (raf != null) {
                    raf.close();
                }
            } catch (IOException ignore) {
            }
            if (resultHandler != null) {
                vertx.runOnContext(v -> resultHandler.handle(Future.failedFuture(e)));
            } else {
                log.error("Failed to send file", e);
            }
        }
        return this;
    }

    @Override
    public SocketAddress remoteAddress() {
        return super.remoteAddress();
    }

    public SocketAddress localAddress() {
        return super.localAddress();
    }

    @Override
    public NetSocketImpl exceptionHandler(Handler<Throwable> handler) {
        return (NetSocketImpl) super.exceptionHandler(handler);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public NetSocketImpl closeHandler(Handler<Void> handler) {
        return (NetSocketImpl) super.closeHandler(handler);
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized void close() {
        if (writeFuture != null) {
            // Close after all data is written
            writeFuture.addListener(ChannelFutureListener.CLOSE);
            channel.flush();
        } else {
            super.close();
        }
    }

    // TODO: 17/1/1 by zmyer
    @Override
    public synchronized NetSocket upgradeToSsl(final Handler<Void> handler) {
        //读取SSL事件处理器
        SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        if (sslHandler == null) {
            if (host != null) {
                sslHandler = helper.createSslHandler(vertx, host, port);
            } else {
                sslHandler = helper.createSslHandler(vertx, this.remoteName(), this.remoteAddress().port());
            }
            //在通道中注册SSL事件处理器
            channel.pipeline().addFirst("ssl", sslHandler);
        }
        sslHandler.handshakeFuture().addListener(future -> context.executeFromIO(() -> {
            if (future.isSuccess()) {
                handler.handle(null);
            } else {
                log.error(future.cause());
            }
        }));
        return this;
    }

    @Override
    public boolean isSsl() {
        return channel.pipeline().get(SslHandler.class) != null;
    }


    @Override
    public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
        return getPeerCertificateChain();
    }

    @Override
    protected synchronized void handleInterestedOpsChanged() {
        checkContext();
        callDrainHandler();
    }

    @Override
    public void end() {
        close();
    }

    // TODO: 17/1/1 by zmyer
    @Override
    protected synchronized void handleClosed() {
        checkContext();
        if (endHandler != null) {
            endHandler.handle(null);
        }
        super.handleClosed();
        if (vertx.eventBus() != null) {
            registration.unregister();
        }
    }

    // TODO: 17/1/1 by zmyer
    synchronized void handleDataReceived(Buffer data) {
        //检查执行上下文对象
        checkContext();
        if (paused) {
            if (pendingData == null) {
                //将接受到的数据缓存到挂起缓冲区中
                pendingData = data.copy();
            } else {
                //追加到挂起缓冲区
                pendingData.appendBuffer(data);
            }
            return;
        }
        if (pendingData != null) {
            //开始从挂起缓冲区中读取数据
            data = pendingData.appendBuffer(data);
            pendingData = null;
        }
        //
        reportBytesRead(data.length());
        if (dataHandler != null) {
            //开始处理接收到的数据
            dataHandler.handle(data);
        }
    }

    private void write(ByteBuf buff) {
        reportBytesWritten(buff.readableBytes());
        writeFuture = super.writeToChannel(buff);
    }

    private synchronized void callDrainHandler() {
        if (drainHandler != null) {
            if (!writeQueueFull()) {
                drainHandler.handle(null);
            }
        }
    }

}

