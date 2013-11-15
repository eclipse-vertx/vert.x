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
package org.vertx.java.core.datagram.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DatagramChannelFutureListener<T> implements ChannelFutureListener {
  private final Handler<AsyncResult<T>> handler;
  private final T result;
  private final DefaultContext context;
  private final VertxInternal vertx;

  DatagramChannelFutureListener(T result, Handler<AsyncResult<T>> handler, VertxInternal vertx, DefaultContext context) {
    this.handler = handler;
    this.result = result;
    this.context = context;
    this.vertx = vertx;
  }

  @Override
  public void operationComplete(final ChannelFuture future) throws Exception {
    Channel ch = future.channel();
    if (context.isOnCorrectWorker(ch.eventLoop())) {
      try {
        vertx.setContext(context);
        notifyHandler(future);
      } catch (Throwable t) {
        context.reportException(t);
      }
    } else {
      context.execute(new Runnable() {
        public void run() {
          notifyHandler(future);
        }
      });
    }

  }

  private void notifyHandler(ChannelFuture future) {
    if (future.isSuccess()) {
      handler.handle(new DefaultFutureResult<>(result));
    } else {
      handler.handle(new DefaultFutureResult<T>(future.cause()));
    }
  }
}
