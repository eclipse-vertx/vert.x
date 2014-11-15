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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.DecoderResult;

/**
 * Helper wrapper class which allows to assemble a LastHttpContent and a HttpResponse into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AssembledFullHttpResponse extends AssembledHttpResponse implements FullHttpResponse {

  public AssembledFullHttpResponse(HttpResponse response, LastHttpContent content) {
    this(response, content.content(), content.trailingHeaders(), content.getDecoderResult());
  }

  public AssembledFullHttpResponse(HttpResponse response) {
    this(response, Unpooled.EMPTY_BUFFER);
  }

  public AssembledFullHttpResponse(HttpResponse response, ByteBuf buf) {
    super(response, toLastContent(buf, null, DecoderResult.SUCCESS));
  }

  public AssembledFullHttpResponse(HttpResponse response, ByteBuf buf, HttpHeaders trailingHeaders, DecoderResult result) {
    super(response, toLastContent(buf, trailingHeaders, result));
  }

  private static LastHttpContent toLastContent(ByteBuf buf, HttpHeaders trailingHeaders, DecoderResult result) {
    if (buf.isReadable()) {
      if (trailingHeaders == null) {
        return new DefaultLastHttpContent(buf);
      } else {
        return new AssembledLastHttpContent(buf, trailingHeaders, result);
      }
    } else {
      if (trailingHeaders == null) {
        return LastHttpContent.EMPTY_LAST_CONTENT;
      } else {
        return new AssembledLastHttpContent(Unpooled.EMPTY_BUFFER, trailingHeaders, result);
      }
    }
  }

  @Override
  public AssembledFullHttpResponse setStatus(HttpResponseStatus status) {
    super.setStatus(status);
    return this;
  }

  @Override
  public AssembledFullHttpResponse retain(int increment) {
    super.retain(increment);
    return this;
  }

  @Override
  public AssembledFullHttpResponse retain() {
    super.retain();
    return this;
  }

  @Override
  public AssembledFullHttpResponse duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledFullHttpResponse copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledFullHttpResponse setProtocolVersion(HttpVersion version) {
    super.setProtocolVersion(version);
    return this;
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return ((LastHttpContent) content).trailingHeaders();
  }
}
