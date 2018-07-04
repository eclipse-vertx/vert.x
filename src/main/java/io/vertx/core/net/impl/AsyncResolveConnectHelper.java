/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.SocketAddress;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncResolveConnectHelper {

  private List<Handler<AsyncResult<Channel>>> handlers = new ArrayList<>();
  private ChannelFuture future;
  private AsyncResult<Channel> result;

  public synchronized void addListener(Handler<AsyncResult<Channel>> handler) {
    if (result != null) {
      if (future != null) {
        future.addListener(v -> handler.handle(result));
      } else {
        handler.handle(result);
      }
    } else {
      handlers.add(handler);
    }
  }

  private synchronized void handle(ChannelFuture cf, AsyncResult<Channel> res) {
    if (result == null) {
      for (Handler<AsyncResult<Channel>> handler: handlers) {
        handler.handle(res);
      }
      future = cf;
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

  public static AsyncResolveConnectHelper doBind(VertxInternal vertx, SocketAddress socketAddress,
                                                 ServerBootstrap bootstrap) {
    AsyncResolveConnectHelper asyncResolveConnectHelper = new AsyncResolveConnectHelper();
    bootstrap.channelFactory(vertx.transport().serverChannelFactory(socketAddress.path() != null));
    if (socketAddress.path() != null) {
      java.net.SocketAddress converted = vertx.transport().convert(socketAddress, true);
      ChannelFuture future = bootstrap.bind(converted);
      future.addListener(f -> {
        if (f.isSuccess()) {
          asyncResolveConnectHelper.handle(future, Future.succeededFuture(future.channel()));
        } else {
          asyncResolveConnectHelper.handle(future, Future.failedFuture(f.cause()));
        }
      });
    } else {
      checkPort(socketAddress.port());
      vertx.resolveAddress(socketAddress.host(), res -> {
        if (res.succeeded()) {
          // At this point the name is an IP address so there will be no resolve hit
          InetSocketAddress t = new InetSocketAddress(res.result(), socketAddress.port());
          ChannelFuture future = bootstrap.bind(t);
          future.addListener(f -> {
            if (f.isSuccess()) {
              asyncResolveConnectHelper.handle(future, Future.succeededFuture(future.channel()));
            } else {
              asyncResolveConnectHelper.handle(future, Future.failedFuture(f.cause()));
            }
          });
        } else {
          asyncResolveConnectHelper.handle(null, Future.failedFuture(res.cause()));
        }
      });
    }
    return asyncResolveConnectHelper;
  }
}
