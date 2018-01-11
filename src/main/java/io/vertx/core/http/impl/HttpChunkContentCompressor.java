/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class HttpChunkContentCompressor extends HttpContentCompressor {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof ByteBuf) {
      // convert ByteBuf to HttpContent to make it work with compression. This is needed as we use the
      // ChunkedWriteHandler to send files when compression is enabled.
      ByteBuf buff = (ByteBuf) msg;
      if (buff.isReadable()) {
        // We only encode non empty buffers, as empty buffers can be used for determining when
        // the content has been flushed and it confuses the HttpContentCompressor
        // if we let it go
        msg = new DefaultHttpContent(buff);
      }
    }
    super.write(ctx, msg, promise);
  }

  HttpChunkContentCompressor(int compressionLevel) {
    super(compressionLevel);
  }

}
