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

package io.vertx.core.internal.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.Socket;

/**
 * Extends to expose Netty interactions for reusing existing Netty codecs:
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface SocketInternal extends Socket {

  /**
   * Returns the {@link ChannelHandlerContext} of the last handler (named {@code handler}) of the pipeline that
   * delivers message to the application with the {@link #handler(Handler)} and {@link #messageHandler(Handler)}.
   * <p/>
   * Handlers should be inserted in the pipeline using {@link io.netty.channel.ChannelPipeline#addBefore(String, String, ChannelHandler)}:
   * <p/>
   * <code><pre>
   *   ChannelPipeline pipeline = so.channelHandlerContext().pipeline();
   *   pipeline.addBefore("handler", "myhandler", new MyHandler());
   * </pre></code>
   * @return the channel handler context
   */
  ChannelHandlerContext channelHandlerContext();

  /**
   * Write a message in the channel pipeline.
   * <p/>
   * When a read operation is in progress, the flush operation is delayed until the read operation completes.
   *
   * Note: this handler does not take in account the eventually pending buffers
   *
   * @param message the message to write, it should be handled by one of the channel pipeline handlers
   * @return a future completed with the result
   */
  Future<Void> writeMessage(Object message);

  /**
   * Set a {@code handler} on this socket to process the messages produced by this socket. The message can be
   * {@link io.netty.buffer.ByteBuf} or other messages produced by channel pipeline handlers.
   * <p/>
   * The {@code} handler should take care of releasing pooled / direct messages.
   * <p/>
   * The handler replaces any {@link #handler(Handler)} previously set.
   *
   * @param handler the handler to set
   * @return a reference to this, so the API can be used fluently
   */
  SocketInternal messageHandler(Handler<Object> handler);

  /**
   * Set a {@code handler} on this socket to process the read complete event produced by this socket. This handler
   * is called when the socket has finished delivering message to the message handler. It should not be used
   * when it comes to buffers, since buffer delivery might be further buffered.
   * <p/>
   * The handler replaces any {@link #handler(Handler)} previously set.
   *
   * @param handler the handler to set
   * @return a reference to this, so the API can be used fluently
   */
  SocketInternal readCompletionHandler(Handler<Void> handler);

  /**
   * Set a handler to process pipeline user events.
   *
   * The handler should take care of releasing event, e.g calling {@code ReferenceCountUtil.release(evt)}.
   *
   * @param handler the handler to set
   * @return a reference to this, so the API can be used fluently
   */
  SocketInternal eventHandler(Handler<Object> handler);

}
