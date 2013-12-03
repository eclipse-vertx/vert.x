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
package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.stream.ChunkedInput;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class HttpContentChunkInput implements ChunkedInput<HttpContent> {

  private final ChunkedInput<ByteBuf> input;

  public HttpContentChunkInput(ChunkedInput<ByteBuf> input) {
    this.input = input;
  }
  @Override
  public boolean isEndOfInput() throws Exception {
    return input.isEndOfInput();
  }

  @Override
  public void close() throws Exception {

  }

  @Override
  public HttpContent readChunk(ChannelHandlerContext ctx) throws Exception {
    ByteBuf buf = input.readChunk(ctx);
    if (buf == null) {
      return null;
    }
    return new DefaultHttpContent(buf);
  }
}
