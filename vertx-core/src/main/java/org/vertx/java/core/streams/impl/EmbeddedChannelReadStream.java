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
import io.netty.channel.ChannelInboundHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.ReadStream;

/**
 * {@link ReadStream} wrapper which allows to make use of {@link ChannelInboundHandler} implementations to process the buffers
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class EmbeddedChannelReadStream extends AbstractEmbeddedChannelStream<EmbeddedChannelReadStream>
        implements ReadStream<EmbeddedChannelReadStream> {

  private final ReadStream<?> stream;

  private Handler<Buffer> handler;
  private Handler<Void> endHandler;

  private EmbeddedChannelReadStream(ReadStream<?> stream, ChannelInboundHandler handler) {
    super(handler);
    this.stream = stream;
  }

  @Override
  protected void finish0() {
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  @Override
  protected void handleOutput(ByteBuf buf) {
    if (handler != null) {
      handler.handle(new Buffer(buf));
    }
  }

  @Override
  public EmbeddedChannelReadStream endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public EmbeddedChannelReadStream dataHandler(Handler<Buffer> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public EmbeddedChannelReadStream pause() {
    stream.pause();
    return this;
  }

  @Override
  public EmbeddedChannelReadStream resume() {
    stream.resume();
    return this;
  }

  public static EmbeddedChannelReadStream create(ReadStream<?> stream, ChannelInboundHandler handler) {
    final EmbeddedChannelReadStream readStream = new EmbeddedChannelReadStream(stream, handler);
    stream.endHandler(new Handler<Void>() {
      @Override
      public void handle(Void event) {
        readStream.finish();
      }
    });
    stream.dataHandler(new Handler<Buffer>() {
      @Override
      public void handle(Buffer event) {
        readStream.handleInput(event);
      }
    });
    stream.exceptionHandler(new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        readStream.handleException(event);
      }
    });
    return readStream;
  }
}
