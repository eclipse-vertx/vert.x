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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.impl.ContextImpl;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
// TODO: 16/12/18 by zmyer
public abstract class VertxHandler<C extends ConnectionBase> extends ChannelDuplexHandler {

    protected abstract C getConnection();

    protected abstract C removeConnection();

    protected ContextImpl getContext(C connection) {
        return connection.getContext();
    }

    // TODO: 16/12/18 by zmyer
    protected static ByteBuf safeBuffer(ByteBuf buf, ByteBufAllocator allocator) {
        if (buf == Unpooled.EMPTY_BUFFER) {
            return buf;
        }
        if (buf.isDirect() || buf instanceof CompositeByteBuf) {
            try {
                if (buf.isReadable()) {
                    //分配缓冲区
                    ByteBuf buffer = allocator.heapBuffer(buf.readableBytes());
                    //向缓冲区写入
                    buffer.writeBytes(buf);
                    return buffer;
                } else {
                    return Unpooled.EMPTY_BUFFER;
                }
            } finally {
                buf.release();
            }
        }
        return buf;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        //首先获取连接
        C conn = getConnection();
        if (conn != null) {
            //从连接对象中获取执行上下文对象
            ContextImpl context = getContext(conn);
            context.executeFromIO(conn::handleInterestedOpsChanged);
        }
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) throws Exception {
        //读取通道对象
        Channel ch = chctx.channel();
        // Don't remove the connection at this point, or the handleClosed won't be called when channelInactive is called!
        //读取连接对象
        C connection = getConnection();
        if (connection != null) {
            //获取执行上下文对象
            ContextImpl context = getContext(connection);
            //开始在执行上下文对象中执行
            context.executeFromIO(() -> {
                try {
                    //首先关闭连接
                    if (ch.isOpen()) {
                        ch.close();
                    }
                } catch (Throwable ignore) {
                }
                //处理异常对象
                connection.handleException(t);
            });
        } else {
            //关闭连接
            ch.close();
        }
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void channelInactive(ChannelHandlerContext chctx) throws Exception {
        //获取待删除的连接对象
        C connection = removeConnection();
        if (connection != null) {
            //获取执行上下文对象
            ContextImpl context = getContext(connection);
            //开始在执行上下文对象执行关闭链接操作
            context.executeFromIO(connection::handleClosed);
        }
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        C conn = getConnection();
        if (conn != null) {
            ContextImpl context = getContext(conn);
            context.executeFromIO(conn::endReadAndFlush);
        }
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
        Object message = safeObject(msg, chctx.alloc());
        C connection = getConnection();

        ContextImpl context;
        if (connection != null) {
            context = getContext(connection);
            //开始读取消息
            context.executeFromIO(connection::startRead);
        } else {
            context = null;
        }
        //从通道对象中读取消息
        channelRead(connection, context, chctx, message);
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
            ctx.close();
        }
        ctx.fireUserEventTriggered(evt);
    }

    protected abstract void channelRead(C connection, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception;

    protected abstract Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception;
}
