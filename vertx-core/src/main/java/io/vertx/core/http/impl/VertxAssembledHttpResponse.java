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
import io.netty.handler.codec.http.*;


/**
 * Helper wrapper class which allows to assemble a HttpContent and a HttpResponse into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class VertxAssembledHttpResponse extends VertxHttpResponse implements HttpContent {

  private final ByteBuf content;

  VertxAssembledHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers) {
    this(head, version, status, headers, Unpooled.EMPTY_BUFFER, false);
  }

  VertxAssembledHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ByteBuf content) {
    this(head, version, status, headers, content, false);
  }

  VertxAssembledHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ByteBuf content, boolean ended) {
    super(head, version, status, headers, ended);
    this.content = content;
  }

  @Override
  public HttpContent copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContent duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContent retainedDuplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpContent replace(ByteBuf content) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VertxAssembledHttpResponse retain() {
    content.retain();
    return this;
  }

  @Override
  public VertxAssembledHttpResponse retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public VertxAssembledHttpResponse setStatus(HttpResponseStatus status) {
    return (VertxAssembledHttpResponse) super.setStatus(status);
  }

  @Override
  public VertxAssembledHttpResponse setProtocolVersion(HttpVersion version) {
    return (VertxAssembledHttpResponse) super.setProtocolVersion(version);
  }

  @Override
  public VertxAssembledHttpResponse touch() {
    content.touch();
    return this;
  }

  @Override
  public VertxAssembledHttpResponse touch(Object hint) {
    content.touch(hint);
    return this;
  }

  @Override
  public ByteBuf content() {
    return content;
  }

  @Override
  public int refCnt() {
    return content.refCnt();
  }

  @Override
  public boolean release() {
    return content.release();
  }

  @Override
  public boolean release(int decrement) {
    return content.release(decrement);
  }
}
