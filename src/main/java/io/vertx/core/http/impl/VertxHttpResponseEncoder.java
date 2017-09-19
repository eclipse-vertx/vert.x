/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
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
