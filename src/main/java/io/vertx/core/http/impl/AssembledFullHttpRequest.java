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
import io.netty.handler.codec.http.*;

/**
 * Helper wrapper class which allows to assemble a LastHttpContent and a HttpRequest into one "packet" and so more
 * efficient write it through the pipeline.
 */
class AssembledFullHttpRequest extends AssembledHttpRequest implements FullHttpRequest {

  public AssembledFullHttpRequest(HttpRequest request, LastHttpContent content) {
    super(request, content);
  }

  public AssembledFullHttpRequest(HttpRequest request) {
    super(request, LastHttpContent.EMPTY_LAST_CONTENT);
  }

  public AssembledFullHttpRequest(HttpRequest request, ByteBuf buf) {
    super(request, toLastContent(buf));
  }

  private static LastHttpContent toLastContent(ByteBuf buf) {
    if (buf.isReadable()) {
      return new DefaultLastHttpContent(buf, false);
    } else {
      return LastHttpContent.EMPTY_LAST_CONTENT;
    }
  }

  @Override
  public AssembledFullHttpRequest setUri(String uri) {
    super.setUri(uri);
    return this;
  }

  @Override
  public AssembledFullHttpRequest setProtocolVersion(HttpVersion version) {
    super.setProtocolVersion(version);
    return this;
  }

  @Override
  public AssembledFullHttpRequest setMethod(HttpMethod method) {
    super.setMethod(method);
    return this;
  }

  @Override
  public AssembledFullHttpRequest duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledFullHttpRequest copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AssembledFullHttpRequest copy(ByteBuf newContent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return ((LastHttpContent) content).trailingHeaders();
  }

  @Override
  public AssembledFullHttpRequest retain() {
    super.retain();
    return this;
  }

  @Override
  public AssembledFullHttpRequest retain(int increment) {
    super.retain(increment);
    return this;
  }

  @Override
  public AssembledFullHttpRequest touch(Object hint) {
    super.touch(hint);
    return this;
  }

  @Override
  public AssembledFullHttpRequest touch() {
    super.touch();
    return this;
  }
}
