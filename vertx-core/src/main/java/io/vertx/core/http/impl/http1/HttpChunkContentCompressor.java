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

package io.vertx.core.http.impl.http1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;

import java.util.List;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class HttpChunkContentCompressor extends HttpContentCompressor {

  public HttpChunkContentCompressor(int contentSizeThreshold, CompressionOptions... compressionOptions) {
    super(contentSizeThreshold, compressionOptions);
  }

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

  @Override
  protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
    super.encode(ctx, msg, out);
    if (msg instanceof HttpResponse) {
      // identity only opts the response out of compression, it is not a content coding to send (RFC 9110 8.4.1).
      // The encoder passes such a response through as is, whichever branch it took, so remove the header here.
      HttpHeaders headers = ((HttpResponse) msg).headers();
      if (headers.contains(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.IDENTITY, true)) {
        headers.remove(HttpHeaderNames.CONTENT_ENCODING);
      }
    }
  }
}
