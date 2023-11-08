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
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientConnection extends HttpConnection {

  Logger log = LoggerFactory.getLogger(HttpClientConnection.class);

  Handler<Void> DEFAULT_EVICTION_HANDLER = v -> {
    log.warn("Connection evicted");
  };

  Handler<Long> DEFAULT_CONCURRENCY_CHANGE_HANDLER = concurrency -> {};

  HostAndPort authority();

  /**
   * Set a {@code handler} called when the connection should be evicted from a pool.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  HttpClientConnection evictionHandler(Handler<Void> handler);

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
   * @return the number of active streams
   */
  long activeStreams();

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
   * @return a future notified with the created stream
   */
  Future<HttpClientRequest> createRequest(ContextInternal context);

  /**
   * Create an HTTP stream.
   *
   * @param context the stream context
   * @return a future notified with the created stream
   */
  Future<HttpClientStream> createStream(ContextInternal context);

  ContextInternal getContext();

  boolean isValid();

  Object metric();

  /**
   * @return the timestamp of the last received response - this is used for LIFO connection pooling
   */
  long lastResponseReceivedTimestamp();

}
