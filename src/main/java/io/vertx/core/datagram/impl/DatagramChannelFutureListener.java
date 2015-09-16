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
final class DatagramChannelFutureListener<T> implements ChannelFutureListener {
  private final Handler<AsyncResult<T>> handler;
  private final T result;
  private final ContextImpl context;

  DatagramChannelFutureListener(T result, Handler<AsyncResult<T>> handler, ContextImpl context) {
    this.handler = handler;
    this.result = result;
    this.context = context;
  }

  @Override
  public void operationComplete(final ChannelFuture future) throws Exception {
    context.executeFromIO(() -> notifyHandler(future));
  }

  private void notifyHandler(ChannelFuture future) {
    if (future.isSuccess()) {
      handler.handle(Future.succeededFuture(result));
    } else {
      handler.handle(Future.failedFuture(future.cause()));
    }
  }
}
