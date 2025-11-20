/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.quic;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicStream;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface QuicConnectionInternal extends QuicConnection {

  ContextInternal context();

  Object metric();

  TransportMetrics<?> metrics();

  QuicConnectionInternal streamContextProvider(Function<ContextInternal, ContextInternal> provider);

  /**
   * Set a handler called when the connection grace period ends, this can be used to operate on connection stream individually before they
   * are closed.
   *
   * @param handler the handler invoked
   * @return literally this
   */
  QuicConnectionInternal graceHandler(Handler<Void> handler);

  Future<QuicStream> openStream(ContextInternal context);

  Future<QuicStream> openStream(ContextInternal context, boolean bidirectional);

  Future<QuicStream> openStream(ContextInternal context, boolean bidirectional, Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>> initializerProvider);

  ChannelHandlerContext channelHandlerContext();

  Future<Void> closeFuture();

}
