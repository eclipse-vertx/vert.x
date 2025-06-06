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
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Helper wrapper class which allows to assemble a LastHttpContent and a HttpRequest into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class VertxFullHttpRequest extends VertxAssembledHttpRequest implements FullHttpRequest {

  public VertxFullHttpRequest(HttpRequest request) {
    super(request, LastHttpContent.EMPTY_LAST_CONTENT, true);
  }

  public VertxFullHttpRequest(HttpRequest request, ByteBuf buf) {
    super(request, toLastContent(buf), true);
  }

  private static LastHttpContent toLastContent(ByteBuf buf) {
    if (buf.isReadable()) {
      return new DefaultLastHttpContent(buf, false);
    } else {
      return LastHttpContent.EMPTY_LAST_CONTENT;
    }
  }

  @Override
  public VertxFullHttpRequest replace(ByteBuf content) {
    super.replace(content);
    return this;
  }

  @Override
  public VertxFullHttpRequest retainedDuplicate() {
    super.retainedDuplicate();
    return this;
  }

  @Override
  public VertxFullHttpRequest setUri(String uri) {
    super.setUri(uri);
    return this;
  }

  @Override
  public VertxFullHttpRequest setProtocolVersion(HttpVersion version) {
    super.setProtocolVersion(version);
    return this;
  }

  @Override
  public VertxFullHttpRequest setMethod(HttpMethod method) {
    super.setMethod(method);
    return this;
  }

  @Override
  public VertxFullHttpRequest duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VertxFullHttpRequest copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return ((LastHttpContent) content).trailingHeaders();
  }

  @Override
  public VertxFullHttpRequest retain() {
    super.retain();
    return this;
  }

  @Override
  public VertxFullHttpRequest retain(int increment) {
    super.retain(increment);
    return this;
  }

  @Override
  public VertxFullHttpRequest touch(Object hint) {
    super.touch(hint);
    return this;
  }

  @Override
  public VertxFullHttpRequest touch() {
    super.touch();
    return this;
  }
}
