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
package org.vertx.java.core.streams.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.impl.PartialPooledByteBufAllocator;
import org.vertx.java.core.streams.ExceptionSupport;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class AbstractEmbeddedChannelStream<T> implements ExceptionSupport<T>{

  protected final EmbeddedChannel channel;
  private Handler<Throwable> errorHandler;
  private final boolean inbound;

  AbstractEmbeddedChannelStream(ChannelHandler handler) {
    this.channel = new EmbeddedChannel(handler);
    channel.config().setAllocator(PartialPooledByteBufAllocator.INSTANCE);
    inbound = handler instanceof ChannelInboundHandler;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final T exceptionHandler(Handler<Throwable> handler) {
    this.errorHandler = handler;
    return (T) this;
  }

  protected final void handleException(Throwable cause) {
    if (errorHandler != null) {
      errorHandler.handle(cause);
    }
  }

  protected final void handleInput(Buffer event) {
    try {
      ByteBuf buf = event.getByteBuf();
      boolean pending;
      if (inbound) {
        pending = channel.writeInbound(buf);
      } else {
        pending = channel.writeOutbound(buf);
      }
      if (pending) {
        processPending();
      }
    } catch (Throwable cause) {
      handleException(cause);
    }
  }

  protected final void finish() {
    try {
      if (channel.finish()) {
        processPending();
      }
    } catch (Throwable cause) {
      handleException(cause);
    } finally {
      finish0();
    }
  }

  protected final void processPending() {
    for (;;) {
      ByteBuf buf;
      if (inbound) {
        buf = (ByteBuf) channel.readInbound();
      } else {
        buf = (ByteBuf) channel.readOutbound();
      }
      if (buf == null) {
        return;
      }
      try {
        handleOutput(buf);
      } catch (Throwable cause) {
        handleException(cause);
      }
    }
  }

  protected abstract void handleOutput(ByteBuf buf);
  protected abstract void finish0();
}
