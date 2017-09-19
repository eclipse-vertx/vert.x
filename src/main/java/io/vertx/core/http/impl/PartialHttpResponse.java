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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;


/**
 * Helper wrapper class which allows to assemble an HttpContent and a HttpResponse into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class PartialHttpResponse extends DefaultHttpResponse implements HttpContent {

  protected final ByteBuf content;

  PartialHttpResponse(HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ByteBuf buf) {
    super(version, status, headers);
    this.content = buf;
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
  public PartialHttpResponse retain() {
    content.retain();
    return this;
  }

  @Override
  public PartialHttpResponse retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public PartialHttpResponse touch() {
    content.touch();
    return this;
  }

  @Override
  public PartialHttpResponse touch(Object hint) {
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
