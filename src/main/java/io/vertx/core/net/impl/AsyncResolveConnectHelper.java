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

package io.vertx.core.net.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Promise;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.SocketAddress;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncResolveConnectHelper {

  public static io.netty.util.concurrent.Future<Channel> doBind(VertxInternal vertx,
                                                                SocketAddress socketAddress,
                                                                ServerBootstrap bootstrap) {
    Promise<Channel> promise = vertx.getAcceptorEventLoopGroup().next().newPromise();
    try {
      bootstrap.channelFactory(vertx.transport().serverChannelFactory(socketAddress.isDomainSocket()));
    } catch (Exception e) {
      promise.setFailure(e);
      return promise;
    }
    if (socketAddress.isDomainSocket()) {
      java.net.SocketAddress converted = vertx.transport().convert(socketAddress);
      ChannelFuture future = bootstrap.bind(converted);
      future.addListener(f -> {
        if (f.isSuccess()) {
          promise.setSuccess(future.channel());
        } else {
          promise.setFailure(f.cause());
        }
      });
    } else {
      SocketAddressImpl impl = (SocketAddressImpl) socketAddress;
      Handler<AsyncResult<InetAddress>> cont = res -> {
        if (res.succeeded()) {
          // At this point the name is an IP address so there will be no resolve hit
          InetSocketAddress t = new InetSocketAddress(res.result(), socketAddress.port());
          ChannelFuture future = bootstrap.bind(t);
          future.addListener(f -> {
            if (f.isSuccess()) {
              promise.setSuccess(future.channel());
            } else {
              promise.setFailure(f.cause());
            }
          });
        } else {
          promise.setFailure(res.cause());
        }
      };
      if (impl.ipAddress() != null) {
        cont.handle(Future.succeededFuture(impl.ipAddress()));
      } else {
        vertx.resolveAddress(socketAddress.host(), cont);
      }
    }
    return promise;
  }
}
