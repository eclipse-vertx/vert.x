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

package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxNetHandler extends VertxHandler<DefaultNetSocket> {

  public VertxNetHandler(VertxInternal vertx, Map<Channel, DefaultNetSocket> connectionMap) {
    super(vertx, connectionMap);
  }

  @Override
  protected void channelRead(final DefaultNetSocket sock, final DefaultContext context, ChannelHandlerContext chctx, Object msg) throws Exception {
    if (sock != null) {
      final ByteBuf buf = (ByteBuf) msg;
      Channel ch = chctx.channel();
      // We need to do this since it's possible the server is being used from a worker context
      if (context.isOnCorrectWorker(ch.eventLoop())) {
        try {
          vertx.setContext(context);
          try {
            sock.handleDataReceived(new Buffer(buf));
          } catch (Throwable t) {
            context.reportException(t);
          }
        } catch (Throwable t) {
          context.reportException(t);
        }
      } else {
        context.execute(new Runnable() {
          public void run() {
            try {
              sock.handleDataReceived(new Buffer(buf));
            } catch (Throwable t) {
              context.reportException(t);
            }
          }
        });
      }
    } else {
      // just discard
    }
  }

  @Override
  protected Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception {
    if (msg instanceof ByteBuf) {
      return safeBuffer((ByteBuf) msg, allocator);
    }
    return msg;
  }
}
