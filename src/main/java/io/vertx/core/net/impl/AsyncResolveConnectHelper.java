/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.net.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 17/1/1 by zmyer
public class AsyncResolveConnectHelper {
    //事件处理器列表
    private List<Handler<AsyncResult<Channel>>> handlers = new ArrayList<>();
    //异步通道对象
    private ChannelFuture future;
    //异步结果对象
    private AsyncResult<Channel> result;

    // TODO: 17/1/1 by zmyer
    public synchronized void addListener(Handler<AsyncResult<Channel>> handler) {
        if (result != null) {
            if (future != null) {
                //为异步对象添加事件处理器对象
                future.addListener(v -> handler.handle(result));
            } else {
                //开始执行事件流程
                handler.handle(result);
            }
        } else {
            //如果还没有结果,将事件处理器插入到事件列表中
            handlers.add(handler);
        }
    }

    // TODO: 17/1/1 by zmyer
    private synchronized void handle(ChannelFuture cf, AsyncResult<Channel> res) {
        if (result == null) {
            //依次遍历每个事件处理器,并开始执行
            for (Handler<AsyncResult<Channel>> handler : handlers) {
                handler.handle(res);
            }
            //设置异步通道对象
            future = cf;
            //设置异步结果对象
            result = res;
        } else {
            throw new IllegalStateException("Already complete!");
        }
    }

    // TODO: 17/1/1 by zmyer
    private static void checkPort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port " + port);
        }
    }

    // TODO: 17/1/1 by zmyer
    public static AsyncResolveConnectHelper doBind(VertxInternal vertx, int port, String host,
                                                   ServerBootstrap bootstrap) {
        //检查端口号
        checkPort(port);
        AsyncResolveConnectHelper asyncResolveConnectHelper = new AsyncResolveConnectHelper();
        vertx.resolveAddress(host, res -> {
            if (res.succeeded()) {
                // At this point the name is an IP address so there will be no resolve hit
                //创建socket地址对象
                InetSocketAddress t = new InetSocketAddress(res.result(), port);
                //开始绑定socket地址对象
                ChannelFuture future = bootstrap.bind(t);
                future.addListener(f -> {
                    if (f.isSuccess()) {
                        //绑定成功
                        asyncResolveConnectHelper.handle(future, Future.succeededFuture(future.channel()));
                    } else {
                        //绑定失败
                        asyncResolveConnectHelper.handle(future, Future.failedFuture(f.cause()));
                    }
                });
            } else {
                //绑定失败
                asyncResolveConnectHelper.handle(null, Future.failedFuture(res.cause()));
            }
        });
        return asyncResolveConnectHelper;
    }
}
