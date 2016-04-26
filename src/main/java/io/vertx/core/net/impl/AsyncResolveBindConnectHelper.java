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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncResolveBindConnectHelper<T> implements Handler<AsyncResult<T>> {

  private List<Handler<AsyncResult<T>>> handlers = new ArrayList<>();
  private boolean complete;
  private AsyncResult<T> result;

  public synchronized void addListener(Handler<AsyncResult<T>> handler) {
    if (complete) {
      handler.handle(result);
    } else {
      handlers.add(handler);
    }
  }

  @Override
  public synchronized void handle(AsyncResult<T> res) {
    if (!complete) {
      for (Handler<AsyncResult<T>> handler: handlers) {
        handler.handle(res);
      }
      complete = true;
      result = res;
    } else {
      throw new IllegalStateException("Already complete!");
    }
  }

  private static void checkPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("Invalid port " + port);
    }
  }

  public static AsyncResolveBindConnectHelper<ChannelFuture> doBind(VertxInternal vertx, int port, String host,
                                                                    ServerBootstrap bootstrap) {
    return doBindConnect(vertx, port, host, bootstrap::bind);
  }

  public static AsyncResolveBindConnectHelper<ChannelFuture> doConnect(VertxInternal vertx, int port, String host,
                                                                       Bootstrap bootstrap) {
    return doBindConnect(vertx, port, host, bootstrap::connect);
  }

  private static AsyncResolveBindConnectHelper<ChannelFuture> doBindConnect(VertxInternal vertx, int port, String host,
                                                                            Function<InetSocketAddress,
                                                                            ChannelFuture> cfProducer) {
    checkPort(port);
    AsyncResolveBindConnectHelper<ChannelFuture> asyncResolveBindConnectHelper = new AsyncResolveBindConnectHelper<>();
    vertx.resolveHostname(host, res -> {
      if (res.succeeded()) {
        // At this point the name is an IP address so there will be no resolve hit
        InetSocketAddress t = new InetSocketAddress(res.result(), port);
        ChannelFuture future = cfProducer.apply(t);
        future.addListener(f -> {
          if (f.isSuccess()) {
            asyncResolveBindConnectHelper.handle(Future.succeededFuture(future));
          } else {
            asyncResolveBindConnectHelper.handle(Future.failedFuture(f.cause()));
          }
        });
      } else {
        asyncResolveBindConnectHelper.handle(Future.failedFuture(res.cause()));
      }
    });
    return asyncResolveBindConnectHelper;
  }

}
