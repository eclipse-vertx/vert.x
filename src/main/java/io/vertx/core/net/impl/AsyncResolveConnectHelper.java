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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncResolveConnectHelper {

  private final VertxInternal vertx;
  private final SocketAddress socketAddress;
  private final ServerBootstrap bootstrap;
  private final AtomicReference<Handlers> handlersRef = new AtomicReference<>(new Handlers());

  private AsyncResolveConnectHelper(VertxInternal vertx, SocketAddress socketAddress, ServerBootstrap bootstrap) {
    this.vertx = vertx;
    this.socketAddress = socketAddress;
    this.bootstrap = bootstrap;
  }

  public void addListener(Handler<AsyncResult<Channel>> handler) {
    boolean queued;
    for (; ; ) {
      Handlers current = this.handlersRef.get();
      if (current.completed()) {
        queued = false;
        break;
      } else {
        Handlers next = current.addHandler(handler);
        if (handlersRef.compareAndSet(current, next)) {
          queued = true;
          break;
        }
      }
    }
    if (!queued) {
      Handlers handlers = this.handlersRef.get();
      if (handlers.failure == null) {
        invokeSuccess(handlers.channelFuture, handler);
      } else {
        invokeFailure(handlers.channelFuture, handlers.failure, handler);
      }
    }
  }

  private static void checkPort(int port) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("Invalid port " + port);
    }
  }

  public static AsyncResolveConnectHelper doBind(VertxInternal vertx, SocketAddress socketAddress, ServerBootstrap bootstrap) {
    AsyncResolveConnectHelper helper = new AsyncResolveConnectHelper(vertx, socketAddress, bootstrap);
    helper.doBind();
    return helper;
  }

  private void doBind() {
    bootstrap.channelFactory(vertx.transport().serverChannelFactory(socketAddress.path() != null));
    if (socketAddress.path() != null) {
      java.net.SocketAddress converted = vertx.transport().convert(socketAddress, true);
      bindAndAddListener(converted);
    } else {
      checkPort(socketAddress.port());
      vertx.resolveAddress(socketAddress.host(), res -> {
        if (res.succeeded()) {
          // At this point the name is an IP address so there will be no resolve hit
          InetSocketAddress inetSocketAddress = new InetSocketAddress(res.result(), socketAddress.port());
          bindAndAddListener(inetSocketAddress);
        } else {
          fail(null, res.cause());
        }
      });
    }
  }

  private void bindAndAddListener(java.net.SocketAddress socketAddress) {
    ChannelFuture future = bootstrap.bind(socketAddress);
    future.addListener(f -> {
      if (f.isSuccess()) {
        complete(future);
      } else {
        fail(future, f.cause());
      }
    });
  }

  private void complete(ChannelFuture channelFuture) {
    Handlers current, next;
    do {
      current = this.handlersRef.get();
      next = current.complete(channelFuture);
    } while (!handlersRef.compareAndSet(current, next));
    next.list.forEach(handler -> invokeSuccess(channelFuture, handler));
  }

  private void invokeSuccess(ChannelFuture channelFuture, Handler<AsyncResult<Channel>> handler) {
    channelFuture.addListener(v -> handler.handle(Future.succeededFuture(channelFuture.channel())));
  }

  private void fail(ChannelFuture channelFuture, Throwable cause) {
    Handlers current, next;
    do {
      current = this.handlersRef.get();
      next = current.fail(channelFuture, cause);
    } while (!handlersRef.compareAndSet(current, next));
    next.list.forEach(handler -> invokeFailure(channelFuture, cause, handler));
  }

  private void invokeFailure(ChannelFuture channelFuture, Throwable cause, Handler<AsyncResult<Channel>> handler) {
    if (channelFuture != null) {
      channelFuture.addListener(v -> handler.handle(Future.failedFuture(cause)));
    } else {
      handler.handle(Future.failedFuture(cause));
    }
  }

  private static class Handlers {
    final ChannelFuture channelFuture;
    final Throwable failure;
    final List<Handler<AsyncResult<Channel>>> list;

    Handlers() {
      channelFuture = null;
      failure = null;
      list = Collections.emptyList();
    }

    Handlers(ChannelFuture channelFuture, Throwable failure, List<Handler<AsyncResult<Channel>>> list) {
      this.channelFuture = channelFuture;
      this.failure = failure;
      this.list = list;
    }

    Handlers complete(ChannelFuture channelFuture) {
      checkComplete();
      return new Handlers(channelFuture, null, list);
    }

    Handlers fail(ChannelFuture channelFuture, Throwable failure) {
      checkComplete();
      return new Handlers(channelFuture, failure, list);
    }

    Handlers addHandler(Handler<AsyncResult<Channel>> handler) {
      checkComplete();
      List<Handler<AsyncResult<Channel>>> nextList = new ArrayList<>(list.size() + 1);
      nextList.addAll(list);
      nextList.add(handler);
      return new Handlers(channelFuture, failure, nextList);
    }

    void checkComplete() {
      if (completed()) {
        throw new IllegalStateException("Already complete!");
      }
    }

    boolean completed() {
      return channelFuture != null || failure != null;
    }
  }
}
