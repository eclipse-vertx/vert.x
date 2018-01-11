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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

/**
 * Helper wrapper class which allows to assemble a LastHttpContent and a HttpResponse into one "packet" and so more
 * efficient write it through the pipeline.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class AssembledFullHttpResponse extends AssembledHttpResponse implements FullHttpResponse {

  private HttpHeaders trailingHeaders;

  public AssembledFullHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ByteBuf buf, HttpHeaders trailingHeaders) {
    super(head, version, status, headers, buf);
    this.trailingHeaders = trailingHeaders;
  }

  @Override
  public HttpHeaders trailingHeaders() {
    return trailingHeaders;
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
    super.duplicate();
    return this;
  }

  @Override
  public AssembledFullHttpResponse copy() {
    super.copy();
    return this;
  }

  @Override
  public AssembledFullHttpResponse retainedDuplicate() {
    super.retainedDuplicate();
    return this;
  }

  @Override
  public AssembledFullHttpResponse replace(ByteBuf content) {
    super.replace(content);
    return this;
  }

  @Override
  public AssembledFullHttpResponse setProtocolVersion(HttpVersion version) {
    super.setProtocolVersion(version);
    return this;
  }

  @Override
  public AssembledFullHttpResponse touch() {
    super.touch();
    return this;
  }

  @Override
  public AssembledFullHttpResponse touch(Object hint) {
    super.touch(hint);
    return this;
  }
}
