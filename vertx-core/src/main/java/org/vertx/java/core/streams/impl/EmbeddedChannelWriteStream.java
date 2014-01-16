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
import io.netty.channel.ChannelOutboundHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.WriteStream;

/**
 * {@link WriteStream} wrapper which allows to make use of {@link ChannelOutboundHandler} implementations to process the buffers
 * which should be written.

 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class EmbeddedChannelWriteStream extends AbstractEmbeddedChannelStream<EmbeddedChannelWriteStream>
        implements WriteStream<EmbeddedChannelWriteStream> {

  private final WriteStream<?> stream;

  private Handler<Void> drainHandler;

  private EmbeddedChannelWriteStream(WriteStream<?> stream, ChannelOutboundHandler handler) {
    super(handler);
    this.stream = stream;
  }

  @Override
  public EmbeddedChannelWriteStream write(Buffer data) {
    handleInput(data);
    return this;
  }

  @Override
  public EmbeddedChannelWriteStream setWriteQueueMaxSize(int maxSize) {
    stream.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return stream.writeQueueFull();
  }

  @Override
  public EmbeddedChannelWriteStream drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    return this;
  }

  @Override
  protected void finish0() {
    stream.close();
  }

  private void handleDrained() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  @Override
  public void close() {
    finish();
  }

  @Override
  protected void handleOutput(ByteBuf buf) {
    stream.write(new Buffer(buf));
  }

  public static WriteStream<?> create(WriteStream<?> stream, ChannelOutboundHandler handler) {
    final EmbeddedChannelWriteStream writeStream = new EmbeddedChannelWriteStream(stream, handler);
    stream.drainHandler(new Handler<Void>() {
      @Override
      public void handle(Void event) {
        writeStream.handleDrained();
      }
    });
    stream.exceptionHandler(new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        writeStream.handleException(event);
      }
    });
    return writeStream;
  }
}
