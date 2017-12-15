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
