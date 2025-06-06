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

package io.vertx.core.http.impl;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.LastHttpContent;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.impl.SysProps;

/**
 * {@link io.netty.handler.codec.http.HttpResponseEncoder} which forces the usage of direct buffers for max performance.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class VertxHttpResponseEncoder extends HttpResponseEncoder {

  private final boolean cacheImmutableResponseHeaders = SysProps.CACHE_IMMUTABLE_HTTP_RESPONSE_HEADERS.getBoolean();

  @Override
  protected void encodeHeaders(HttpHeaders headers, ByteBuf buf) {
    if (headers instanceof HeadersMultiMap) {
      HeadersMultiMap vertxHeaders = (HeadersMultiMap) headers;
      vertxHeaders.encode(buf, cacheImmutableResponseHeaders);
    } else {
      super.encodeHeaders(headers, buf);
    }
  }

  @Override
  public boolean acceptOutboundMessage(Object msg) throws Exception {
    // fast-path singleton(s)
    if (msg == Unpooled.EMPTY_BUFFER || msg == LastHttpContent.EMPTY_LAST_CONTENT) {
      return true;
    }
    // fast-path exact class matches: we cannot use a (concrete) class type check
    // here because the contract of HttpResponseEncoder::acceptOutboundMessage
    // enforces msg to NOT implement HttpRequest and we don't know if users extends vertx/netty types to
    // implement it.
    final Class<?> msgClazz = msg.getClass();
    if (msgClazz == VertxFullHttpResponse.class ||
      msgClazz == DefaultFullHttpResponse.class ||
      msgClazz == VertxAssembledHttpResponse.class ||
      msgClazz == DefaultHttpContent.class ||
      msgClazz == VertxLastHttpContent.class ||
      msgClazz == DefaultFileRegion.class) {
      return true;
    }
    // Netty slow-path
    return super.acceptOutboundMessage(msg);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
  }

  @Override
  protected boolean isContentAlwaysEmpty(HttpResponse msg) {
    // In HttpServerCodec this is tracked via a FIFO queue of HttpMethod
    // here we track it in the assembled response as we don't use HttpServerCodec
    return (msg instanceof VertxAssembledHttpResponse && ((VertxAssembledHttpResponse) msg).head()) || super.isContentAlwaysEmpty(msg);
  }
}
