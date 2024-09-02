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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientConnectionInternal extends HttpConnection {

  Logger log = LoggerFactory.getLogger(HttpClientConnectionInternal.class);

  Handler<Void> DEFAULT_EVICTION_HANDLER = v -> {
    log.warn("Connection evicted");
  };

  Handler<Long> DEFAULT_CONCURRENCY_CHANGE_HANDLER = concurrency -> {};

  /**
   * @return the number of active request/response (streams)
   */
  long activeStreams();

  /**
   * @return the max number of active streams this connection can handle concurrently
   */
  long concurrency();

  HostAndPort authority();

  /**
   * Set a {@code handler} called when the connection should be evicted from a pool.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  HttpClientConnectionInternal evictionHandler(Handler<Void> handler);

  /**
   * Set a {@code handler} called when the connection concurrency changes.
   * The handler is called with the new concurrency.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  HttpClientConnectionInternal concurrencyChangeHandler(Handler<Long> handler);

  /**
   * @return whether the connection is pooled
   */
  boolean pooled();

  /**
   * @return the {@link ChannelHandlerContext} of the handler managing the connection
   */
  ChannelHandlerContext channelHandlerContext();

  /**
   * Create an HTTP stream.
   *
   * @param context the stream context
   * @return a future notified with the created stream
   */
  Future<HttpClientStream> createStream(ContextInternal context);

  /**
   * @return the connection context
   */
  ContextInternal context();

  boolean isValid();

  Object metric();

  /**
   * @return the timestamp of the last received response - this is used for LIFO connection pooling
   */
  long lastResponseReceivedTimestamp();

}
