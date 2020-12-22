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

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientConnection extends HttpConnection {

  Logger log = LoggerFactory.getLogger(HttpClientConnection.class);

  Handler<Boolean> DEFAULT_LIFECYCLE_HANDLER = status -> {
    log.warn("Connection " + (status ? "evicted" : "recycled"));
  };

  Handler<Long> DEFAULT_CONCURRENCY_CHANGE_HANDLER = concurrency -> {};

  /**
   * Set a {@code handler} called when the connection lifecycle changes.
   * When the handler is called with {@code true} the connection can be recycled
   * otherwise it is considered to be closed.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  HttpClientConnection lifecycleHandler(Handler<Boolean> handler);

  /**
   * Set a {@code handler} called when the connection concurrency changes.
   * The handler is called with the new concurrency.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  HttpClientConnection concurrencyChangeHandler(Handler<Long> handler);

  /**
   * @return the connection concurrency
   */
  long concurrency();

  /**
   * @return the connection channel
   */
  Channel channel();

  /**
   * @return the {@link ChannelHandlerContext} of the handler managing the connection
   */
  ChannelHandlerContext channelHandlerContext();

  /**
   * Create an HTTP stream.
   *
   * @param context the stream context
   * @param handler the handler called when the stream is created
   */
  void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler);

  ContextInternal getContext();

  boolean isValid();

  Object metric();

}
