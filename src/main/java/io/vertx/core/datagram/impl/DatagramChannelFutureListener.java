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

package io.vertx.core.datagram.impl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DatagramChannelFutureListener<T> implements ChannelFutureListener {
  private final Handler<AsyncResult<T>> handler;
  private final T result;
  private final ContextInternal context;

  DatagramChannelFutureListener(T result, Handler<AsyncResult<T>> handler, ContextInternal context) {
    this.handler = handler;
    this.result = result;
    this.context = context;
  }

  @Override
  public void operationComplete(final ChannelFuture future) throws Exception {
    Future<T> res = future.isSuccess() ? Future.succeededFuture(result) : Future.failedFuture(future.cause());
    context.executeFromIO(res, handler);
  }
}
