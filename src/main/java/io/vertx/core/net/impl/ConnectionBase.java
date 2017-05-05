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

import io.netty.channel.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetworkOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;

/**
 * Abstract base class for TCP connections.
 * <p>
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 * <p>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/18 by zmyer
public abstract class ConnectionBase {

    private static final Logger log = LoggerFactory.getLogger(ConnectionBase.class);
    //vertx对象
    protected final VertxInternal vertx;
    //通道对象
    protected final Channel channel;
    //上下文对象
    protected final ContextImpl context;
    //网络统计对象
    protected final NetworkMetrics metrics;
    //异常处理对象
    private Handler<Throwable> exceptionHandler;
    //关闭处理对象
    private Handler<Void> closeHandler;
    //可读取标记
    private boolean read;
    //刷新标记
    private boolean needsFlush;
    //上下文执行线程
    private Thread ctxThread;
    //异步刷新标记
    private boolean needsAsyncFlush;

    // TODO: 16/12/18 by zmyer
    protected ConnectionBase(VertxInternal vertx, Channel channel, ContextImpl context, NetworkMetrics metrics) {
        this.vertx = vertx;
        this.channel = channel;
        this.context = context;
        this.metrics = metrics;
    }

    // TODO: 16/12/18 by zmyer
    protected ConnectionBase(VertxInternal vertx, Channel channel, ContextImpl context, NetworkOptions options) {
        this.vertx = vertx;
        this.channel = channel;
        this.context = context;
        this.metrics = createMetrics(options);
    }

    protected NetworkMetrics createMetrics(NetworkOptions options) {
        return null;
    }

    // TODO: 16/12/18 by zmyer
    protected synchronized final void startRead() {
        //检查执行上下文对象
        checkContext();
        read = true;
        if (ctxThread == null) {
            //设置上下文执行线程对象
            ctxThread = Thread.currentThread();
        }
    }

    // TODO: 16/12/18 by zmyer
    protected synchronized final void endReadAndFlush() {
        read = false;
        if (needsFlush) {
            needsFlush = false;
            if (needsAsyncFlush) {
                // If the connection has been written to from outside the event loop thread e.g. from a worker thread
                // Then Netty might end up executing the flush *before* the write as Netty checks for event loop and if not
                // it executes using the executor.
                // To avoid this ordering issue we must runOnContext in this situation
                //开始在执行上下文对象中执行通道刷新操作
                context.runOnContext(v -> channel.flush());
            } else {
                // flush now
                //刷新通道
                channel.flush();
            }
        }
    }

    // TODO: 16/12/18 by zmyer
    public synchronized ChannelFuture queueForWrite(final Object obj) {
        needsFlush = true;
        needsAsyncFlush = Thread.currentThread() != ctxThread;
        //向通道对象中写入消息
        return channel.write(obj);
    }

    // TODO: 16/12/18 by zmyer
    public synchronized ChannelFuture writeToChannel(Object obj) {
        if (read) {
            //开始写入对象
            return queueForWrite(obj);
        }
        //如果通道对象可读,则直接写入
        if (channel.isOpen()) {
            return channel.writeAndFlush(obj);
        } else {
            return null;
        }
    }

    // This is a volatile read inside the Netty channel implementation
    public boolean isNotWritable() {
        return !channel.isWritable();
    }

    /**
     * Close the connection
     */
    // TODO: 16/12/18 by zmyer
    public void close() {
        // make sure everything is flushed out on close
        endReadAndFlush();
        channel.close();
    }

    // TODO: 16/12/18 by zmyer
    public synchronized ConnectionBase closeHandler(Handler<Void> handler) {
        closeHandler = handler;
        return this;
    }

    // TODO: 16/12/18 by zmyer
    public synchronized ConnectionBase exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    // TODO: 16/12/18 by zmyer
    protected synchronized Handler<Throwable> exceptionHandler() {
        return exceptionHandler;
    }

    public void doPause() {
        channel.config().setAutoRead(false);
    }

    public void doResume() {
        channel.config().setAutoRead(true);
    }

    // TODO: 16/12/18 by zmyer
    public void doSetWriteQueueMaxSize(int size) {
        //读取通道对象配置对象
        ChannelConfig config = channel.config();
        int high = config.getWriteBufferHighWaterMark();
        int newLow = size / 2;
        int newHigh = size;
        if (newLow >= high) {
            config.setWriteBufferHighWaterMark(newHigh);
            config.setWriteBufferLowWaterMark(newLow);
        } else {
            config.setWriteBufferLowWaterMark(newLow);
            config.setWriteBufferHighWaterMark(newHigh);
        }
    }

    // TODO: 16/12/18 by zmyer
    protected void checkContext() {
        // Sanity check
        if (context != vertx.getContext()) {
            throw new IllegalStateException("Wrong context!");
        }
    }


    protected ContextImpl getContext() {
        return context;
    }

    protected abstract Object metric();

    // TODO: 16/12/18 by zmyer
    protected synchronized void handleException(Throwable t) {
        metrics.exceptionOccurred(metric(), remoteAddress(), t);
        if (exceptionHandler != null) {
            exceptionHandler.handle(t);
        } else {
            log.error(t);
        }
    }

    // TODO: 16/12/18 by zmyer
    protected synchronized void handleClosed() {
        if (metrics instanceof TCPMetrics) {
            ((TCPMetrics) metrics).disconnected(metric(), remoteAddress());
        }
        if (closeHandler != null) {
            closeHandler.handle(null);
        }
    }

    protected abstract void handleInterestedOpsChanged();

    // TODO: 16/12/18 by zmyer
    protected void addFuture(final Handler<AsyncResult<Void>> completionHandler, final ChannelFuture future) {
        if (future != null) {
            //设置监听器
            future.addListener(channelFuture -> context.executeFromIO(() -> {
                if (completionHandler != null) {
                    if (channelFuture.isSuccess()) {
                        completionHandler.handle(Future.succeededFuture());
                    } else {
                        completionHandler.handle(Future.failedFuture(channelFuture.cause()));
                    }
                } else if (!channelFuture.isSuccess()) {
                    handleException(channelFuture.cause());
                }
            }));
        }
    }

    protected boolean supportsFileRegion() {
        return !isSSL();
    }

    // TODO: 16/12/18 by zmyer
    public void reportBytesRead(long numberOfBytes) {
        if (metrics.isEnabled()) {
            metrics.bytesRead(metric(), remoteAddress(), numberOfBytes);
        }
    }

    // TODO: 16/12/18 by zmyer
    public void reportBytesWritten(long numberOfBytes) {
        if (metrics.isEnabled()) {
            metrics.bytesWritten(metric(), remoteAddress(), numberOfBytes);
        }
    }

    private boolean isSSL() {
        return channel.pipeline().get(SslHandler.class) != null;
    }

    // TODO: 16/12/18 by zmyer
    protected ChannelFuture sendFile(RandomAccessFile raf, long offset, long length) throws IOException {
        // Write the content.
        ChannelFuture writeFuture;
        //如果不支持文件块
        if (!supportsFileRegion()) {
            // Cannot use zero-copy
            //开始向通道写入
            writeFuture = writeToChannel(new ChunkedFile(raf, offset, length, 8192));
        } else {
            // No encryption - use zero-copy.
            //创建默认的文件块对象
            FileRegion region = new DefaultFileRegion(raf.getChannel(), offset, length);
            //向通道对象中写入文件块对象
            writeFuture = writeToChannel(region);
        }
        if (writeFuture != null) {
            //注册监听器
            writeFuture.addListener(fut -> raf.close());
        } else {
            raf.close();
        }
        return writeFuture;
    }

    // TODO: 16/12/18 by zmyer
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        if (isSSL()) {
            ChannelHandlerContext sslHandlerContext = channel.pipeline().context("ssl");
            assert sslHandlerContext != null;
            SslHandler sslHandler = (SslHandler) sslHandlerContext.handler();
            return sslHandler.engine().getSession().getPeerCertificateChain();
        } else {
            return null;
        }
    }

    // TODO: 16/12/18 by zmyer
    public String remoteName() {
        InetSocketAddress addr = (InetSocketAddress) channel.remoteAddress();
        if (addr == null) return null;
        // Use hostString that does not trigger a DNS resolution
        return addr.getHostString();
    }

    // TODO: 16/12/18 by zmyer
    public SocketAddress remoteAddress() {
        InetSocketAddress addr = (InetSocketAddress) channel.remoteAddress();
        if (addr == null) return null;
        return new SocketAddressImpl(addr);
    }

    // TODO: 16/12/18 by zmyer
    public SocketAddress localAddress() {
        InetSocketAddress addr = (InetSocketAddress) channel.localAddress();
        if (addr == null) return null;
        return new SocketAddressImpl(addr);
    }
}
