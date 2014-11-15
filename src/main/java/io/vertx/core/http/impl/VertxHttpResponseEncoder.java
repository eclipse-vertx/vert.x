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
package io.vertx.core.http.impl;


import io.netty.channel.ChannelHandlerContext;
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
}
