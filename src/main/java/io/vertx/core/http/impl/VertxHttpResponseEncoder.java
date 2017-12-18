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


import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;

import java.util.List;

/**
 * {@link io.netty.handler.codec.http.HttpResponseEncoder} which forces the usage of direct buffers for max performance.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class VertxHttpResponseEncoder extends HttpResponseEncoder {
  private ChannelHandlerContext context;

  @Override
  protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
    super.encode(context, msg, out);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    this.context = PartialPooledByteBufAllocator.forceDirectAllocator(ctx);
    super.handlerAdded(ctx);
  }

  @Override
  protected boolean isContentAlwaysEmpty(HttpResponse msg) {
    // In HttpServerCodec this is tracked via a FIFO queue of HttpMethod
    // here we track it in the assembled response as we don't use HttpServerCodec
    return msg instanceof AssembledHttpResponse && ((AssembledHttpResponse) msg).head();
  }
}
