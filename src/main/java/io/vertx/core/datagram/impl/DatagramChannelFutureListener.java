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
import io.netty.channel.ChannelFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
// TODO: 16/12/18 by zmyer
final class DatagramChannelFutureListener<T> implements ChannelFutureListener {
    //处理事件对象
    private final Handler<AsyncResult<T>> handler;
    //处理的结果对象
    private final T result;
    //上下文对象
    private final ContextImpl context;

    // TODO: 16/12/18 by zmyer
    DatagramChannelFutureListener(T result, Handler<AsyncResult<T>> handler, ContextImpl context) {
        this.handler = handler;
        this.result = result;
        this.context = context;
    }

    // TODO: 16/12/18 by zmyer
    @Override
    public void operationComplete(final ChannelFuture future) throws Exception {
        //操作完成,开始进行对结果的处理流程
        context.executeFromIO(() -> notifyHandler(future));
    }

    // TODO: 16/12/18 by zmyer
    private void notifyHandler(ChannelFuture future) {
        if (future.isSuccess()) {
            //开始处理结果
            handler.handle(Future.succeededFuture(result));
        } else {
            handler.handle(Future.failedFuture(future.cause()));
        }
    }
}
